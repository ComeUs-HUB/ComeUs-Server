package com.toyproject.backend.domain.user.dto;

/**
 * [9단계 · K] 회원 정보 수정 요청 DTO — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code PUT /api/users/me}의 요청 바디.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>record. <b>모든 필드가 optional</b>이다 — 카카오 로그인만으로 가입되므로 프로필은
 *       나중에 조금씩 채워지고, 입력을 강제하지 않는다.</li>
 *   <li>{@code @NotNull}/{@code @NotBlank}를 붙이지 않는다. 붙이면 부분 수정이 불가능해진다.</li>
 *   <li>형식 검증({@code @Size}, {@code @Pattern}, {@code @Past})만 건다 — <b>값이 있을 때만</b> 동작한다.</li>
 * </ul>
 *
 * <p><b>예정 컴포넌트</b>
 * <ul>
 *   <li>{@code name} — 길이 제한</li>
 *   <li>{@code phone} — 휴대폰 번호 형식</li>
 *   <li>{@code address} — 길이 제한</li>
 *   <li>{@code birthDate} — 과거 날짜</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * "null = 변경하지 않음" 규약이므로, 값을 <b>비우는</b> 수단은 이 API에 없다.
 * 필요해지면 별도 설계가 필요하다(빈 문자열과 null의 구분 등).
 */
public record UserUpdateRequest(/* 구현 단계에서 정의 */) {
}
