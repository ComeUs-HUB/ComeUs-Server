package com.toyproject.backend.domain.user.controller;

/**
 * [8단계 · K] 회원 API — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 로그인한 사용자의 내 정보 조회·수정·탈퇴 엔드포인트.
 *
 * <p><b>API</b>
 * <table border="1">
 *   <tr><th>Method</th><th>URI</th><th>요청</th><th>응답</th></tr>
 *   <tr><td>GET</td><td>{@code /api/users/me}</td><td>-</td>
 *       <td>{@code {id, email, name, phone, address, birthDate, role, provider, createdAt}}</td></tr>
 *   <tr><td>PUT</td><td>{@code /api/users/me}</td><td>{@code {name?, phone?, address?, birthDate?}} 모두 optional</td>
 *       <td>위 GET과 동일</td></tr>
 *   <tr><td>DELETE</td><td>{@code /api/users/me}</td><td>-</td><td>{@code null} + 쿠키 만료 {@code Set-Cookie}</td></tr>
 * </table>
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>전부 인증 필요. 로그인 사용자 식별은 {@code @AuthenticationPrincipal UserPrincipal}로 <b>일관되게</b> 한다
 *       (경로 변수나 요청 바디의 userId를 신뢰하지 않는다).</li>
 *   <li>PUT은 {@code @Valid}로 형식 검증. 실패는 {@code GlobalExceptionHandler}가 C001로 처리한다.</li>
 *   <li>응답은 공통 {@code ApiResponse}로 감싼다.</li>
 * </ul>
 *
 * <p><b>주의</b><br>
 * DELETE(탈퇴)는 발급된 refresh token이 전부 삭제되므로 <b>쿠키도 함께 만료</b>시켜야 한다.
 * 이때도 {@code Path=/api/auth}를 함께 지정하지 않으면 브라우저에서 실제로 지워지지 않는다.
 */
public class UserController {
}
