package com.toyproject.backend.domain.user.client.dto;

/**
 * [3단계 · G] 카카오 사용자 정보 응답 DTO — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code GET https://kapi.kakao.com/v2/user/me} 응답 매핑. 회원 생성·조회의 원천 데이터다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>record</b> + {@code @JsonNaming(SnakeCaseStrategy)}. 중첩 구조라
 *       {@code KakaoAccount}, {@code Profile}을 중첩 record로 함께 선언한다.</li>
 *   <li>중첩 필드가 통째로 빠질 수 있으므로 {@code resolveEmail()}·{@code resolveNickname()} 같은
 *       <b>null 안전 헬퍼</b>를 두어 호출부가 깊은 null 체크를 하지 않게 한다.</li>
 * </ul>
 *
 * <p><b>예정 구조</b>
 * <ul>
 *   <li>{@code id} — 카카오 고유 사용자 id(Long). {@code User.providerId}의 원천</li>
 *   <li>{@code kakaoAccount.email} — <b>nullable</b></li>
 *   <li>{@code kakaoAccount.profile.nickname} — {@code User.name} 초기값</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * 이메일은 <b>동의 항목</b>이라 사용자가 거부하면 {@code kakao_account}에 아예 내려오지 않는다.
 * email이 없다고 가입을 실패시키면 안 된다 — null로 두고 진행한다.
 */
public record KakaoUserInfoResponse(/* 구현 단계에서 정의 */) {
}
