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
 * [7단계 · I·K] 인증 서비스
 *
 * <p><b>역할</b><br>
 * 재발급·로그아웃·탈퇴 보조의 비즈니스 로직. HTTP(쿠키·리다이렉트)는 전혀 모르고,
 * 그 조립은 {@code AuthController}가 맡는다.
 *
 * <p>로그인 본체({@link #kakaoLogin})는 8단계에서 붙였다. 나머지(재발급·로그아웃·탈퇴 보조)는
 * 7단계에서 컨트롤러 없이 먼저 검증했다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>클래스에 {@code @Transactional(readOnly = true)}, 쓰기 메서드에만 {@code @Transactional}.</li>
 *   <li>refresh token <b>원문은 인자·반환값으로만</b> 흘려보낸다. 필드·로그·응답 DTO에 남기지 않는다.</li>
 *   <li>회원 데이터 접근은 {@code UserService}를 <b>반드시 경유</b>한다.
 *       {@code UserRepository}를 직접 주입하지 않는다 — 회원 생성 규칙이 auth로 새어나오는 것을 막는다.
 *       (이번 단계에는 회원을 읽는 경로 자체가 없다. {@code reissue}는
 *       {@code @EntityGraph}로 함께 로딩된 {@code RefreshToken.user}를 쓴다.)</li>
 * </ul>
 *
 * <p><b>탈퇴 흐름에서 {@code UserController}가 호출하는 메서드</b><br>
 * {@link #revokeAllTokens(Long)}(fail-closed) → {@code userService.withdraw} →
 * {@link #unlinkKakao(String)}(best-effort) 순이며, <b>호출 순서는 {@code UserController}가 소유</b>한다.
 * 여기서 {@code UserService.withdraw}를 부르지 않는다.
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
     * 인가 코드를 받아 로그인을 성립시킨다. <b>콜백의 본체</b>다.
     *
     * <ol>
     *   <li>{@code requestToken(code)} → {@code fetchUserInfo()} — 카카오 왕복 2회</li>
     *   <li>{@code userService.findOrCreateKakaoUser(...)} — 조회·재활성화·신규 생성 판단은
     *       <b>전부 user 도메인이 소유</b>한다. 여기서는 카카오가 준 값을 넘길 뿐이다</li>
     *   <li>refresh 원문 생성 → 해시를 {@code RefreshToken} row로 저장 → <b>원문을 반환</b></li>
     * </ol>
     *
     * <p><b>여기서 access token을 발급하지 않는다.</b> FE가 복귀 후 {@code POST /api/auth/refresh}로 얻는다.
     * 그 결과 access token이 URL에 실리는 경로가 <b>설계상 존재하지 않고</b>,
     * 로그인 직후와 새로고침 후의 세션 획득 경로가 같아진다.
     *
     * <p><b>구간이 둘로 나뉘고, 경계가 정확히 그 사이에 있다.</b>
     * <ul>
     *   <li><b>카카오 왕복 — 트랜잭션 밖.</b> {@code @Transactional}로 열면 왕복 2회
     *       (connect 3s + read 5s씩) 내내 DB 커넥션을 점유한다. 카카오가 느려지면
     *       커넥션 풀이 먼저 마른다</li>
     *   <li><b>DB 작업 — 하나의 트랜잭션.</b> {@link #issueLoginSession} 참고</li>
     * </ul>
     *
     * <p>⚠️ <b>{@code Propagation.NOT_SUPPORTED}를 유지하는 이유</b><br>
     * 클래스의 {@code readOnly = true}가 카카오 왕복까지 감싸는 것을 막는다.
     * 물려받으면 Hibernate가 {@code FlushMode.MANUAL}로 동작해 <b>안쪽 INSERT가 조용히 사라진다</b>
     * (예외도 나지 않아 로그인은 성공한 것처럼 보이는데 재발급만 A007로 실패한다).
     * 안쪽 {@code TransactionTemplate}은 별개로 자기 트랜잭션을 열므로 충돌하지 않는다.
     *
     * @param code 카카오가 콜백 쿼리로 넘긴 인가 코드 (1회용)
     * @throws BusinessException 카카오 호출 실패 시 A005/A006 ({@code KakaoOAuthClient}가 변환)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KakaoLoginResult kakaoLogin(String code) {
        KakaoTokenResponse token = kakaoOAuthClient.requestToken(code);
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.fetchUserInfo(token.accessToken());

        return issueLoginSessionRetryingOnSignupRace(userInfo);
    }

    /**
     * 로그인 DB 구간을 실행하고, <b>가입 경합이면 새 트랜잭션에서 딱 한 번 다시 시도한다.</b>
     *
     * <p><b>무엇을 막는가</b><br>
     * {@code findOrCreateKakaoUser}의 조회와 저장은 원자적이지 않다. 같은 카카오 계정의 <b>최초</b>
     * 동시 로그인이 둘 다 "미가입"으로 읽으면 늦은 쪽이 유니크 제약에 걸린다.
     * 유니크 제약이 있어 <b>중복 회원이 생기지는 않지만</b>, 그대로 두면 이 양성 경합이
     * {@code ?error=C003}("서버 내부 오류")으로 나가 서버 결함처럼 보인다.
     *
     * <p><b>왜 재시도만으로 충분한가</b><br>
     * 2회차에는 경쟁자가 이미 커밋돼 있어 기존 코드가 두 충돌을 그대로 해소한다.
     * <table border="1">
     *   <tr><th>충돌</th><th>2회차 동작</th></tr>
     *   <tr><td>{@code uk_users_provider_provider_id}</td>
     *       <td>조회가 경쟁자를 찾아 <b>기존 회원으로 재사용</b>({@code isNewUser=false})</td></tr>
     *   <tr><td>{@code uk_users_email}</td>
     *       <td>{@code existsByEmail}이 이제 {@code true} → <b>email만 비우고 가입 진행</b></td></tr>
     * </table>
     * 그래서 <b>제약명으로 충돌 종류를 구분하지 않는다.</b> 제약명·예외 메시지는 DB 벤더마다 다른데
     * 테스트는 H2, 운영은 MySQL이라 그 분기는 정작 운영과 다른 모양으로만 검증된다.
     *
     * <p><b>왜 여기인가</b> — {@code UserService}가 아니라 트랜잭션 경계에서 재시도한다.
     * <ul>
     *   <li>{@code execute}가 <b>새 트랜잭션</b>을 열므로, 제약 위반으로 rollback-only가 된
     *       트랜잭션 안에서 복구를 시도하는 문제가 애초에 없다</li>
     *   <li>⚠️ <b>카카오 왕복이 트랜잭션 밖이라 재실행되지 않는다</b> — 1회용 인가 코드를
     *       재사용하지 않는다는 뜻이다. <b>이 전제가 이 메서드를 성립시킨다.</b>
     *       왕복을 트랜잭션 안으로 옮기면 재시도가 코드를 재사용해 A005로 실패한다</li>
     *   <li>회원 생성 규칙은 user 도메인에 그대로 남는다. 경합 복구는 유스케이스를 조립하는 쪽의 몫이다</li>
     * </ul>
     *
     * <p><b>재시도는 정확히 1회다.</b> 2차 실패는 경합이 아니라 진짜 제약 문제이므로 삼키지 않고 올려보낸다.
     *
     * <p>로그에 {@code providerId}를 남기지 않는다 — 외부 서비스 식별자라 {@code UserResponse}에서도
     * 의도적으로 뺀 값이고, 경합이 일어났다는 사실만으로 진단에 충분하다.
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
     * 회원 해소와 refresh token 발급을 <b>한 트랜잭션에서</b> 수행한다.
     *
     * <p><b>왜 묶는가</b><br>
     * 나뉘어 있으면 회원만 생성되고 토큰 발급이 실패하는 상태가 생긴다. 로그인 자체는 재시도로
     * 복구되지만(같은 회원을 찾아 토큰을 새로 발급한다) <b>{@code isNewUser}는 복구되지 않는다</b> —
     * 2차 시도에서 기존 회원으로 잡혀 {@code false}가 되고, FE의 "추가 정보 입력" UX가 영영 뜨지 않는다.
     * 재시도로 지워지지 않는 유일한 손실이라 여기만 원자적이면 된다.
     *
     * <p>⚠️ <b>{@code @Transactional}을 붙이지 않는다.</b> 자기 호출은 프록시를 타지 않아
     * 애노테이션이 <b>조용히 무효</b>가 된다 — 컴파일도 되고 테스트도 통과하는데 트랜잭션만 안 열린다.
     * 경계의 소유자는 호출자의 {@code transactionTemplate.execute}이며,
     * 이 메서드는 <b>그 안에서만</b> 호출한다.
     *
     * <p>{@code findOrCreateKakaoUser}는 {@code REQUIRED}라 바깥 트랜잭션에 <b>참여</b>한다
     * (새로 열지 않는다). 그 덕에 반환된 {@code User}가 managed 상태로 유지되어
     * {@code RefreshToken.issue}에 detached 엔티티가 넘어가지 않는다.
     *
     * <p>회원 생성 규칙은 여전히 user 도메인이 소유한다 — auth는 <b>트랜잭션 경계만</b> 정한다.
     * 쿼리도 조회 1 + 삽입 1~2뿐이라 트랜잭션이 길어지지 않는다.
     *
     * @return <b>절대 {@code null}이 아니다.</b> {@code TransactionTemplate.execute}의 반환이
     *         {@code @Nullable}이라 호출부 타입이 nullable로 보이지만, 이 메서드가 그 계약을 좁힌다
     */
    private KakaoLoginResult issueLoginSession(KakaoUserInfoResponse userInfo) {
        KakaoUserResolution resolution = userService.findOrCreateKakaoUser(
                userInfo.providerId(), userInfo.resolveEmail(), userInfo.resolveNickname());

        return new KakaoLoginResult(issueRefreshToken(resolution.user()), resolution.isNewUser());
    }

    /**
     * refresh token으로 access token을 재발급한다.
     *
     * <p><b>유효성 = row 존재 AND 미만료.</b> 둘 중 하나라도 아니면 {@code REFRESH_TOKEN_NOT_FOUND}(A007)다 —
     * "없음"과 "만료"를 같은 코드로 응답해 토큰 존재 여부를 흘리지 않는다.
     *
     * <p>rotation을 하지 않으므로 <b>기존 row를 건드리지 않고 새 쿠키도 내려보내지 않는다.</b>
     * 여러 탭이 같은 토큰으로 동시에 호출해도 모두 성공한다.
     *
     * <p>만료된 row는 <b>지우지 않고 그대로 둔다.</b> {@code expiresAt} 검사가 이미 거절하므로 기능상 무해하고,
     * 정리 배치는 후속 과제다.
     *
     * @param rawRefreshToken 쿠키에서 꺼낸 <b>원문</b> (해시가 아니다)
     * @throws BusinessException A007 — 쿠키 없음/빈 값, 미존재, 만료
     */
    public TokenResponse reissue(String rawRefreshToken) {
        RefreshToken refreshToken = findValidToken(rawRefreshToken);

        return TokenResponse.of(
                jwtProvider.createAccessToken(refreshToken.getUser()),
                jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * 요청한 기기의 refresh token 1건을 무효화한다.
     *
     * <p><b>멱등하다.</b> 쿠키가 없거나 이미 삭제된 토큰이어도 예외 없이 성공한다 —
     * {@code deleteByTokenHash}가 없는 해시에 대해 {@code 0}을 반환하므로 <b>삭제 건수를 검사하지 않는다.</b>
     * 로그아웃은 "세션을 없애 달라"는 요청이고, 이미 없으면 그 요청은 이미 충족된 것이다.
     *
     * <p>{@code token_hash}가 유니크라 다른 기기의 세션은 유지된다(멀티 디바이스 전제).
     *
     * @param rawRefreshToken 쿠키에서 꺼낸 원문. {@code null}·빈 값 허용
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(secureTokenGenerator.sha256Hex(rawRefreshToken));
    }

    /**
     * 한 회원의 refresh token을 전부 무효화한다. <b>회원 탈퇴의 1단계</b>다.
     *
     * <p><b>fail-closed</b> — 예외를 잡지 않는다. 실패하면 그대로 전파되어 탈퇴가 중단되고
     * 회원은 ACTIVE로 남는다(재로그인으로 복구 가능). 토큰이 살아 있는데 회원만 WITHDRAWN인 상태보다
     * 안전하다.
     */
    @Transactional
    public void revokeAllTokens(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    /**
     * 카카오 계정 연결을 해제한다. <b>회원 탈퇴의 마지막 단계</b>다.
     *
     * <p><b>best-effort</b> — 실패해도 로그만 남기고 예외를 던지지 않는다. 카카오 장애로 탈퇴가 막히면
     * 안 되기 때문이다. <b>예외를 삼키는 판단은 호출자가 아니라 여기서 한다</b> —
     * {@code KakaoOAuthClient}는 평소처럼 던지고, 그 정책을 이 메서드가 소유한다.
     *
     * <p>DB를 건드리지 않는 외부 호출이라 쓰기 트랜잭션을 열지 않는다.
     */
    public void unlinkKakao(String providerId) {
        try {
            kakaoOAuthClient.unlink(providerId);
        } catch (BusinessException e) {
            log.warn("카카오 연결 해제 실패 — 탈퇴는 그대로 진행한다. providerId={}, code={}",
                    providerId, e.getErrorCode().getCode());
        }
    }

    /**
     * 인가 요청에 실을 {@code state}를 만든다. CSRF 방어용이며 {@code oauth_state} 쿠키와 대조된다.
     *
     * <p>{@code SecureTokenGenerator}를 재사용한다 — 여기서 {@code SecureRandom}을 새로 만들지 않는다.
     * state는 쿠키 값과 대조만 하면 되므로 refresh token과 같은 43자로 충분하다.
     */
    public String generateOAuthState() {
        return secureTokenGenerator.generate();
    }

    /**
     * refresh token의 수명. {@code RefreshToken.expiresAt} 계산과 쿠키 {@code Max-Age}가
     * <b>같은 값</b>을 쓰도록 한 곳에서 제공한다 (둘이 어긋나면 쿠키는 살아 있는데 서버가 거절하거나 그 반대가 된다).
     *
     * <p>설정값은 ms이므로 여기서 {@code Duration}으로 감싼다. JWT의 {@code exp}가 아니다.
     */
    public Duration getRefreshTokenMaxAge() {
        return Duration.ofMillis(jwtProperties.refreshExpiration());
    }

    /**
     * refresh token을 발급해 <b>해시만</b> 저장하고 원문을 돌려준다.
     *
     * <p>{@code expiresAt}과 쿠키 {@code Max-Age}가 어긋나지 않도록 둘 다
     * {@link #getRefreshTokenMaxAge()} 한 곳에서 나온다.
     *
     * <p>원문은 반환값으로만 흘러나가 컨트롤러의 {@code Set-Cookie}에서 끝난다 — 필드·로그에 남기지 않는다.
     *
     * <p>{@code save}는 {@link #issueLoginSession}이 연 <b>바깥 트랜잭션에 참여</b>한다.
     * 여기서 실패하면 같은 트랜잭션의 회원 생성까지 함께 롤백된다.
     */
    private String issueRefreshToken(User user) {
        String rawRefreshToken = secureTokenGenerator.generate();

        refreshTokenRepository.save(RefreshToken.issue(
                user,
                secureTokenGenerator.sha256Hex(rawRefreshToken),
                LocalDateTime.now().plus(getRefreshTokenMaxAge())));

        return rawRefreshToken;
    }

    /**
     * 원문을 SHA-256으로 해시해 조회하고 만료를 검사한다.
     *
     * <p>서버는 원문을 보관하지 않으므로 <b>조회 키는 반드시 해시</b>다.
     * 실패 사유(빈 값·미존재·만료)를 구분하지 않고 전부 A007로 뭉갠다.
     */
    private RefreshToken findValidToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return refreshTokenRepository.findByTokenHash(secureTokenGenerator.sha256Hex(rawRefreshToken))
                .filter(token -> !token.isExpired())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }
}
