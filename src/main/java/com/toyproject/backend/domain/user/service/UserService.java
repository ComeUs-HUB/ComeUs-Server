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
 * [7단계 · K] 회원 서비스
 *
 * <p><b>역할</b><br>
 * 회원 프로필 조회·수정과 회원 생명주기(가입·탈퇴). <b>회원 레코드를 만드는 규칙은 전부 여기에 있다</b> —
 * auth 도메인은 카카오에서 받은 값만 넘기고 생성 규칙은 알지 못한다.
 *
 * <p>7단계에서 auth가 호출하는 경계 메서드({@link #findOrCreateKakaoUser}, {@link #withdraw})를,
 * 9단계에서 프로필 조회·수정({@link #getMyInfo}, {@link #updateMyInfo})을 붙였다.
 * 넷 모두 {@link #getActiveUser(Long)}이나 그에 준하는 조회를 공유한다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code userId}는 {@code @AuthenticationPrincipal}에서 받은 값이므로 소유권 검사가 따로 필요 없다
 *       (자기 자신만 다루는 API다).</li>
 *   <li>상태 변경은 엔티티의 도메인 메서드({@code reactivate}, {@code withdraw})에 위임하고
 *       더티 체킹으로 반영한다. 서비스에서 필드를 직접 세팅하거나 {@code save}를 다시 부르지 않는다.</li>
 *   <li><b>{@code UserRepository}는 user 패키지 밖으로 노출하지 않는다.</b>
 *       auth가 회원 데이터에 접근하는 경로는 이 클래스의 공개 메서드뿐이다.</li>
 * </ul>
 *
 * <p><b>{@code AuthErrorCode}를 참조하는 이유</b><br>
 * 의존 방향은 {@code auth → user} 단방향이지만, <b>ErrorCode enum은 공유 어휘이지 동작 의존이 아니다.</b>
 * 경계에서 막는 것은 auth의 <i>컴포넌트</i>({@code AuthService}·{@code RefreshTokenRepository}·
 * {@code KakaoOAuthClient})를 주입하는 일이며, 그런 주입은 여기에 없다.
 * 문서도 A008({@code WITHDRAWN_USER})을 "회원 상태에서 파생되지만 인증 거절 사유로 소비되므로 auth에 둔다"고
 * 이미 정리해 두었다 — A001도 같은 성격이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * 내 정보 조회. {@code GET /api/users/me}
     *
     * <p>필터가 이미 {@code findById}로 회원을 읽었지만 그 결과({@code UserPrincipal})는
     * {@code id}·{@code role}·{@code status}만 담고 있어 응답 바디를 만들 수 없다.
     * 그래서 여기서 엔티티를 <b>다시 읽는다</b> — 요청 1회당 조회가 2회인 것은
     * "JWT claim만 믿지 않고 매 요청 최신 상태를 확인한다"는 6단계 결정의 비용이다.
     */
    public UserResponse getMyInfo(Long userId) {
        return UserResponse.from(getActiveUser(userId));
    }

    /**
     * 내 정보 수정. {@code PUT /api/users/me}
     *
     * <p>{@code null}인 필드는 <b>변경하지 않는다.</b> 판단은 서비스가 아니라
     * {@code User.updateProfile}이 소유하고, 변경은 더티 체킹으로 반영된다 —
     * 여기서 필드를 직접 세팅하거나 {@code save}를 부르지 않는다.
     *
     * <p>수정 후의 엔티티를 그대로 응답으로 바꾼다. 트랜잭션이 아직 열려 있어
     * 아직 flush되지 않았지만, 우리가 방금 바꾼 값이므로 반환값과 커밋될 값이 같다.
     */
    @Transactional
    public UserResponse updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = getActiveUser(userId);
        user.updateProfile(request.name(), request.phone(), request.address(), request.birthDate());

        return UserResponse.from(user);
    }

    /**
     * 카카오 로그인 시 회원을 찾거나 만든다. <b>{@code AuthService}가 호출하는 진입점</b>이다.
     *
     * <p>세 갈래로 갈린다.
     * <ol>
     *   <li>가입 이력 있음 · ACTIVE → 그대로 사용 ({@code isNewUser=false})</li>
     *   <li>가입 이력 있음 · WITHDRAWN → {@code reactivate()}로 자동 재활성화.
     *       <b>기존 회원이므로 {@code isNewUser=false}</b></li>
     *   <li>가입 이력 없음 → 신규 생성 ({@code isNewUser=true})</li>
     * </ol>
     *
     * @param providerId 카카오 고유 사용자 id
     * @param email      카카오 이메일. 미동의면 {@code null}
     * @param nickname   카카오 닉네임. 미동의면 {@code null}
     */
    @Transactional
    public KakaoUserResolution findOrCreateKakaoUser(String providerId, String email, String nickname) {
        return userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                .map(this::reuse)
                .orElseGet(() -> create(providerId, email, nickname));
    }

    /**
     * 회원 탈퇴 — <b>소프트 삭제만</b> 수행하고 카카오 {@code unlink}에 필요한 {@code providerId}를 돌려준다.
     *
     * <p>refresh token 삭제와 카카오 연결 해제는 <b>여기서 하지 않는다.</b> auth 도메인의 책임이고,
     * 호출 순서는 {@code UserController}가 소유한다. user가 auth를 참조하지 않아야
     * 의존 방향이 {@code auth → user} 단방향으로 유지된다.
     *
     * @return 카카오 고유 사용자 id
     * @throws BusinessException 회원이 없으면 A001(401), 이미 탈퇴했으면 A008(403) ({@link #getActiveUser} 참고)
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
     * 신규 가입에 쓸 이메일을 고른다. <b>중복이어도 가입을 막지 않는다</b> — email만 비우고 진행한다.
     *
     * <p>⚠️ {@code email}이 {@code null}이면 {@code existsByEmail}을 <b>호출하지 않는다.</b>
     * 넘기면 SQL이 {@code email = null}이 되어 항상 false를 반환하는, 의미 없는 쿼리가 나간다.
     *
     * @return 채택한 이메일. 미동의거나 이미 사용 중이면 {@code null}
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
     * <p><b>여기에 도달하는 실패는 "없는 회원"이 아니라 경합이다.</b> {@code userId}는 항상
     * {@code @AuthenticationPrincipal}에서 오고, 그 앞에서 {@code CustomUserDetailsService}가
     * 매 요청 DB를 다시 읽어 미존재와 탈퇴를 이미 걸러낸다. 그러니 이 시점의 실패는
     * <b>필터 통과 직후에 상태가 바뀐 경우</b>뿐이다 — 탈퇴 요청을 두 번 눌렀을 때가 가장 현실적이다.
     *
     * <p><b>거부 사유를 필터와 동일하게 구분해 던진다.</b>
     * <table border="1">
     *   <tr><th>상황</th><th>ErrorCode</th><th>필터에서의 대응</th></tr>
     *   <tr><td>row 없음</td><td>{@code UNAUTHORIZED}(A001, 401)</td>
     *       <td>{@code UsernameNotFoundException}</td></tr>
     *   <tr><td>탈퇴 회원</td><td>{@code WITHDRAWN_USER}(A008, 403)</td>
     *       <td>{@code DisabledException}</td></tr>
     * </table>
     *
     * <p>⚠️ <b>둘을 A001로 뭉개지 말 것.</b> 같은 회원 상태가 어느 계층이 먼저 알아채느냐에 따라
     * 401/403으로 갈리면, 탈퇴 더블 클릭에서 응답이 흔들린다. A007이 미존재와 만료를 뭉개는 것은
     * <b>토큰 존재 여부를 흘리지 않으려는 정보 은닉</b>이 목적이라 사정이 다르다 —
     * 여기서는 자기 자신의 상태이고 이미 A008로 알려주고 있어 은닉할 대상이 없다.
     *
     * <p>미존재를 404가 아니라 401로 내리는 이유: 이 API는 클라이언트가 회원을 <b>지목하지 않는다</b>
     * (경로에 {@code {userId}}가 없다). "네가 지목한 것이 없다"가 아니라 "세션이 더는 유효하지 않다"이므로,
     * FE의 401 인터셉터가 그대로 재로그인으로 흡수하게 한다.
     *
     * <p>두 분기 모두 정상 운영에서 거의 발생하지 않으므로, 진단을 위해 {@code userId}를 로그로 남긴다.
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
