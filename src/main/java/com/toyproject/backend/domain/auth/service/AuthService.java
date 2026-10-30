package com.toyproject.backend.domain.auth.service;

import com.toyproject.backend.domain.auth.client.KakaoOAuthClient;
import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.config.JwtProperties;
import com.toyproject.backend.domain.auth.dto.KakaoLoginResult;
import com.toyproject.backend.domain.auth.dto.TokenResponse;
import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.repository.RefreshTokenRepository;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.auth.token.SecureTokenGenerator;
import com.toyproject.backend.domain.user.dto.KakaoUserResolution;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * [Phase 2 · 7단계] 재발급·로그아웃·탈퇴 보조의 비즈니스 로직.
 * HTTP(쿠키·리다이렉트)는 모르고 그 조립은 {@code AuthController}가 맡는다.
 *
 * <p>refresh token 원문은 인자·반환값으로만 흘려보낸다. 필드·로그·응답 DTO에 남기지 않는다.
 * <p>회원 데이터는 {@code UserService}를 반드시 경유한다 — 회원 생성 규칙이 auth로 새지 않게 한다.
 * <p>탈퇴 흐름의 호출 순서는 {@code UserController}가 소유한다. 계약은
 * {@code docs/domains/auth.md}의 "회원 탈퇴".
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 인가 코드를 받아 로그인을 성립시킨다. 콜백의 본체다.
     *
     * <p>여기서 access token을 발급하지 않는다. FE가 복귀 후 {@code /api/auth/refresh}로 얻어,
     * access token이 URL에 실리는 경로가 설계상 존재하지 않는다.
     * <p>{@code NOT_SUPPORTED}는 클래스의 {@code readOnly}가 카카오 왕복까지 감싸는 것을 막는다.
     * 물려받으면 {@code FlushMode.MANUAL}이라 안쪽 INSERT가 예외 없이 사라진다(ADR 0009).
     *
     * @param code 카카오가 콜백 쿼리로 넘긴 인가 코드 (1회용)
     * @throws BusinessException 카카오 호출 실패 시 A005/A006
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KakaoLoginResult kakaoLogin(String code) {
        KakaoTokenResponse token = kakaoOAuthClient.requestToken(code);
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.fetchUserInfo(token.accessToken());

        return issueLoginSessionRetryingOnSignupRace(userInfo);
    }

    /**
     * 로그인 DB 구간을 실행하고, 가입 경합이면 새 트랜잭션에서 한 번만 다시 시도한다.
     *
     * <p>무엇을 막는지와 왜 재시도만으로 충분한지는 ADR 0009.
     * <p>로그에 {@code providerId}를 남기지 않는다 — 경합 사실만으로 진단에 충분하다.
     */
    private KakaoLoginResult issueLoginSessionRetryingOnSignupRace(KakaoUserInfoResponse userInfo) {
        try {
            return transactionTemplate.execute(status -> issueLoginSession(userInfo));
        } catch (DataIntegrityViolationException e) {
            log.info("가입 경합 — 같은 회원을 만드는 로그인이 먼저 커밋됐다. 새 트랜잭션에서 1회 재시도한다");
            return transactionTemplate.execute(status -> issueLoginSession(userInfo));
        }
    }

    /**
     * 회원 해소와 refresh token 발급을 한 트랜잭션에서 수행한다.
     *
     * <p>나뉘면 회원만 생성되고 토큰이 실패하는 상태가 생긴다. 로그인은 재시도로 복구되지만
     * {@code isNewUser}는 복구되지 않아 FE의 "추가 정보 입력"이 영영 뜨지 않는다.
     * <p>{@code @Transactional}을 붙이지 않는다 — 자기 호출은 프록시를 타지 않아 조용히 무효가
     * 된다. 경계는 호출자의 {@code transactionTemplate}이 소유하고 그 안에서만 부른다.
     *
     * @return {@code null}이 아니다. {@code execute}의 {@code @Nullable} 계약을 여기서 좁힌다
     */
    private KakaoLoginResult issueLoginSession(KakaoUserInfoResponse userInfo) {
        KakaoUserResolution resolution = userService.findOrCreateKakaoUser(
                userInfo.providerId(), userInfo.resolveEmail(), userInfo.resolveNickname());

        return new KakaoLoginResult(issueRefreshToken(resolution.user()), resolution.isNewUser());
    }

    /**
     * refresh token으로 access token을 재발급한다.
     *
     * <p>rotation을 하지 않아 기존 row를 건드리지 않고 새 쿠키도 내려보내지 않는다(ADR 0004).
     * 여러 탭이 같은 토큰으로 동시에 불러도 모두 성공한다.
     * <p>만료 row는 지우지 않는다 — {@code expiresAt} 검사가 이미 거절한다(ADR 0005).
     *
     * @param rawRefreshToken 쿠키에서 꺼낸 원문. 해시가 아니다
     * @throws BusinessException A007 — 없음과 만료를 같은 코드로 응답해 존재 여부를 흘리지 않는다
     */
    public TokenResponse reissue(String rawRefreshToken) {
        RefreshToken refreshToken = findValidToken(rawRefreshToken);

        return TokenResponse.of(
                jwtProvider.createAccessToken(refreshToken.getUser()),
                jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * 요청한 기기의 refresh token 1건을 무효화한다. 다른 기기 세션은 유지된다.
     *
     * <p>멱등하다 — 쿠키가 없거나 이미 삭제됐어도 삭제 건수를 검사하지 않고 성공한다.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(secureTokenGenerator.sha256Hex(rawRefreshToken));
    }

    /** 탈퇴 1단계. fail-closed라 실패하면 탈퇴가 중단되고 회원은 ACTIVE로 남는다. */
    @Transactional
    public void revokeAllTokens(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    /**
     * 카카오 연결 해제. 탈퇴의 마지막 단계이고 best-effort다.
     *
     * <p>삼키는 판단을 호출자가 아니라 여기서 한다 — {@code KakaoOAuthClient}는 평소처럼 던진다.
     */
    public void unlinkKakao(String providerId) {
        try {
            kakaoOAuthClient.unlink(providerId);
        } catch (BusinessException e) {
            log.warn("카카오 연결 해제 실패 — 탈퇴는 그대로 진행한다. providerId={}, code={}",
                    providerId, e.getErrorCode().getCode());
        }
    }

    /** 인가 요청에 실을 CSRF 방어값. {@code SecureTokenGenerator}를 재사용한다. */
    public String generateOAuthState() {
        return secureTokenGenerator.generate();
    }

    /** {@code expiresAt}과 쿠키 {@code Max-Age}가 어긋나지 않도록 한 곳에서 제공한다. */
    public Duration getRefreshTokenMaxAge() {
        return Duration.ofMillis(jwtProperties.refreshExpiration());
    }

    /**
     * refresh token을 발급해 해시만 저장하고 원문을 돌려준다(ADR 0005).
     *
     * <p>원문은 반환값으로만 흘러나가 컨트롤러의 {@code Set-Cookie}에서 끝난다.
     * <p>{@code save}는 바깥 트랜잭션에 참여한다 — 실패하면 회원 생성까지 함께 롤백된다.
     */
    private String issueRefreshToken(User user) {
        String rawRefreshToken = secureTokenGenerator.generate();

        refreshTokenRepository.save(RefreshToken.issue(
                user,
                secureTokenGenerator.sha256Hex(rawRefreshToken),
                LocalDateTime.now().plus(getRefreshTokenMaxAge())));

        return rawRefreshToken;
    }

    /** 조회 키는 반드시 해시다. 빈 값·미존재·만료를 구분하지 않고 전부 A007로 뭉갠다. */
    private RefreshToken findValidToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return refreshTokenRepository.findByTokenHash(secureTokenGenerator.sha256Hex(rawRefreshToken))
                .filter(token -> !token.isExpired())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }
}
