package com.toyproject.backend.domain.user.service;

import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.user.dto.KakaoUserResolution;
import com.toyproject.backend.domain.user.dto.UserResponse;
import com.toyproject.backend.domain.user.dto.UserUpdateRequest;
import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Phase 2 · 7단계] 회원 프로필 조회·수정과 생명주기(가입·탈퇴).
 * 회원 레코드를 만드는 규칙은 전부 여기에 있고 auth는 카카오에서 받은 값만 넘긴다.
 *
 * <p>{@code UserRepository}를 패키지 밖으로 노출하지 않는다 — auth가 회원 데이터에 닿는
 * 경로는 이 클래스의 공개 메서드뿐이다.
 * <p>상태 변경은 엔티티의 도메인 메서드에 위임하고 더티 체킹으로 반영한다.
 * <p>{@code AuthErrorCode}를 참조하지만 ErrorCode enum은 공유 어휘이지 동작 의존이 아니다 —
 * auth의 컴포넌트를 주입하지는 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * 내 정보 조회.
     *
     * <p>필터가 읽은 {@code UserPrincipal}은 응답 바디를 만들 수 없어 엔티티를 다시 읽는다.
     * 요청당 조회 2회는 매 요청 최신 상태를 확인하는 비용이다(ADR 0021).
     */
    public UserResponse getMyInfo(Long userId) {
        return UserResponse.from(getActiveUser(userId));
    }

    /**
     * 내 정보 수정. {@code null}인 필드는 변경하지 않는다.
     *
     * <p>판단은 {@code User.updateProfile}이 소유하고 더티 체킹으로 반영된다.
     * <p>flush 전이지만 방금 바꾼 값이라 반환값과 커밋될 값이 같다.
     */
    @Transactional
    public UserResponse updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = getActiveUser(userId);
        user.updateProfile(request.name(), request.phone(), request.address(), request.birthDate());

        return UserResponse.from(user);
    }

    /**
     * [Phase 4 · 2단계] 활성 회원 엔티티 조회. 장바구니 생성이 쓴다.
     *
     * @throws BusinessException 회원이 없으면 A001(401), 탈퇴했으면 A008(403)
     */
    public User getUser(Long userId) {
        return getActiveUser(userId);
    }

    /**
     * 카카오 로그인 시 회원을 찾거나 만든다. {@code AuthService}의 진입점이다.
     *
     * <p>WITHDRAWN이면 재활성화하되 기존 회원이므로 {@code isNewUser=false}다.
     *
     * @param email    카카오 이메일. 미동의면 {@code null}
     * @param nickname 카카오 닉네임. 미동의면 {@code null}
     */
    @Transactional
    public KakaoUserResolution findOrCreateKakaoUser(String providerId, String email, String nickname) {
        return userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                .map(this::reuse)
                .orElseGet(() -> create(providerId, email, nickname));
    }

    /**
     * 회원 탈퇴 — 소프트 삭제만 하고 카카오 {@code unlink}에 쓸 {@code providerId}를 돌려준다.
     *
     * <p>토큰 삭제와 연결 해제는 auth의 책임이고 순서는 {@code UserController}가 소유한다 —
     * user가 auth를 참조하지 않아야 의존 방향이 단방향으로 유지된다.
     *
     * @throws BusinessException 회원이 없으면 A001(401), 이미 탈퇴했으면 A008(403)
     */
    @Transactional
    public String withdraw(Long userId) {
        User user = getActiveUser(userId);
        user.withdraw();
        return user.getProviderId();
    }

    /**
     * 가입 이력이 있는 회원을 재사용한다. WITHDRAWN이면 재활성화한다.
     *
     * <p>{@code reactivate()}는 더티 체킹으로 반영되므로 {@code save}를 부르지 않는다.
     */
    private KakaoUserResolution reuse(User user) {
        if (user.isWithdrawn()) {
            user.reactivate();
        }
        return KakaoUserResolution.existing(user);
    }

    private KakaoUserResolution create(String providerId, String email, String nickname) {
        User user = userRepository.save(User.ofKakao(providerId, adoptableEmail(email), nickname));
        return KakaoUserResolution.created(user);
    }

    /**
     * 신규 가입에 쓸 이메일을 고른다. 중복이어도 막지 않고 email만 비운다.
     *
     * <p>{@code null}이면 {@code existsByEmail}을 부르지 않는다 — 항상 false를 내는 쿼리다.
     */
    private String adoptableEmail(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.existsByEmail(email) ? null : email;
    }

    /**
     * 인증된 요청의 {@code userId}로 활성 회원을 읽는다.
     *
     * <p>여기 도달하는 실패는 "없는 회원"이 아니라 경합이다 — 필터가 이미 걸러냈으므로
     * 통과 직후 상태가 바뀐 경우뿐이고, 탈퇴 더블 클릭이 가장 현실적이다.
     * <p>필터와 같은 규칙으로 사유를 구분한다. 둘을 A001로 뭉개면 같은 상태가 어느 계층이
     * 먼저 알아채느냐에 따라 401/403으로 흔들린다. 근거는 {@code docs/domains/auth.md}.
     *
     * @throws BusinessException row가 없으면 A001(401), 탈퇴했으면 A008(403)
     */
    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("인증 통과 후 회원 소실 — userId={}", userId);
                    return new BusinessException(AuthErrorCode.UNAUTHORIZED);
                });

        if (user.isWithdrawn()) {
            log.warn("인증 통과 후 탈퇴 — userId={}", userId);
            throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
        }
        return user;
    }
}
