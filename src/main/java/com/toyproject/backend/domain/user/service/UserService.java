package com.toyproject.backend.domain.user.service;

/**
 * [7단계 · K] 회원 서비스 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 회원 프로필 조회·수정과 회원 생명주기(가입·탈퇴). <b>회원 레코드를 만드는 규칙은 전부 여기에 있다</b> —
 * auth 도메인은 카카오에서 받은 값만 넘기고 생성 규칙은 알지 못한다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code userId}는 {@code @AuthenticationPrincipal}에서 받은 값이므로 소유권 검사가 따로 필요 없다
 *       (자기 자신만 다루는 API다).</li>
 *   <li>수정은 엔티티의 {@code updateProfile}에 위임하고 더티 체킹으로 반영한다.
 *       서비스에서 필드를 직접 세팅하지 않는다.</li>
 *   <li><b>{@code UserRepository}는 user 패키지 밖으로 노출하지 않는다.</b>
 *       auth가 회원 데이터에 접근하는 경로는 아래 공개 메서드뿐이다.</li>
 * </ul>
 *
 * <p><b>예정 메서드</b>
 * <ul>
 *   <li>{@code UserResponse getMyInfo(Long userId)}</li>
 *   <li>{@code UserResponse updateMyInfo(Long userId, UserUpdateRequest)} — null 필드는 미변경</li>
 *   <li>{@code KakaoUserResolution findOrCreateKakaoUser(String providerId, String email, String nickname)}
 *       — 카카오 로그인 시 {@code AuthService}가 호출하는 진입점.
 *       조회된/생성된 {@code User}와 신규 생성 여부를 함께 반환한다</li>
 *   <li>{@code String withdraw(Long userId)} — 소프트 삭제 후 카카오 {@code unlink}에 필요한
 *       {@code providerId}를 반환한다</li>
 * </ul>
 *
 * <p><b>{@code findOrCreateKakaoUser} 규칙</b>
 * <ol>
 *   <li>{@code findByProviderAndProviderId(KAKAO, providerId)}로 조회</li>
 *   <li>ACTIVE면 그대로 사용 / WITHDRAWN이면 {@code reactivate()}(자동 재활성화).
 *       둘 다 기존 회원이므로 {@code isNewUser=false}</li>
 *   <li>없으면 신규 생성({@code isNewUser=true}).
 *       <b>email이 이미 쓰이고 있으면 email만 null로 두고 가입은 계속 진행</b>한다
 *       (중복 이메일로 소셜 가입이 막히면 안 된다)</li>
 * </ol>
 *
 * <p><b>{@code withdraw} 처리</b><br>
 * {@code user.withdraw()}로 소프트 삭제({@code status=WITHDRAWN}, {@code withdrawnAt} 기록)만 하고
 * {@code providerId}를 반환한다. <b>refresh token 삭제와 카카오 {@code unlink}는 여기서 하지 않는다</b> —
 * auth 도메인의 책임이고, 호출 순서는 {@code UserController}가 소유한다.
 * user가 auth를 참조하지 않아야 의존 방향이 {@code auth → user} 단방향으로 유지된다.
 *
 * <p><b>주의</b><br>
 * 탈퇴 회원 조회는 "없음"으로 취급한다(이미 탈퇴한 계정에 다시 접근하는 경우).
 * 단 {@code findOrCreateKakaoUser}는 예외다 — WITHDRAWN 계정을 찾아 재활성화하는 것이 목적이다.
 */
public class UserService {
}
