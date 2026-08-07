package com.toyproject.backend.domain.user.dto;

/**
 * [2단계 · G·K] 카카오 회원 조회/생성 결과 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code UserService.findOrCreateKakaoUser}의 반환 타입. user 도메인이 auth 도메인에게
 * "이 회원이다 + 이번에 새로 만들었다/아니다"를 함께 알려주는 내부 전달용 record다.
 *
 * <p><b>구성</b>
 * <ul>
 *   <li>{@code User user} — 조회했거나 새로 만든 회원</li>
 *   <li>{@code boolean isNewUser} — 이번 호출에서 신규 생성되었으면 {@code true}.
 *       WITHDRAWN 계정을 재활성화한 경우는 <b>기존 회원이므로 {@code false}</b>다</li>
 * </ul>
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>신규 여부를 아는 쪽은 회원을 만든 user 도메인뿐이다. auth가 {@code createdAt} 비교 같은
 *       추측으로 판정하지 않도록 값으로 명시해 넘긴다.</li>
 *   <li>{@code AuthService}는 이 값을 {@code KakaoLoginResult.isNewUser}로 그대로 옮기고,
 *       {@code AuthController}가 FE 복귀 URL의 {@code ?isNewUser=true}로 내보낸다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * HTTP 응답 바디에 직렬화되는 DTO가 아니다. 엔티티({@code User})를 그대로 담고 있으므로
 * 컨트롤러 밖으로 나가지 않는다.
 */
public record KakaoUserResolution(/* 구현 단계에서 정의 */) {
}
