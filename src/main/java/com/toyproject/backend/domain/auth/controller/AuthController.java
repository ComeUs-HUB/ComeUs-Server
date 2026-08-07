package com.toyproject.backend.domain.auth.controller;

/**
 * [8단계 · K] 인증 API — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * 로그인 왕복(302·쿠키)과 토큰 재발급·로그아웃의 HTTP 계약을 담당한다.
 *
 * <p><b>API</b>
 * <table border="1">
 *   <tr><th>Method</th><th>URI</th><th>요청</th><th>응답</th></tr>
 *   <tr><td>GET</td><td>{@code /api/auth/kakao/authorize}</td><td>-</td>
 *       <td><b>302</b> → 카카오 + {@code Set-Cookie: oauth_state}</td></tr>
 *   <tr><td>GET</td><td>{@code /api/auth/kakao/callback}</td><td>{@code ?code=&state=}(카카오가 채움)</td>
 *       <td><b>302</b> → FE + {@code Set-Cookie: refresh_token}</td></tr>
 *   <tr><td>POST</td><td>{@code /api/auth/refresh}</td><td>없음(쿠키)</td>
 *       <td>{@code {accessToken, tokenType, accessTokenExpiresIn}} — {@code Set-Cookie} 없음</td></tr>
 *   <tr><td>POST</td><td>{@code /api/auth/logout}</td><td>없음(쿠키)</td>
 *       <td>{@code null} + 쿠키 만료 {@code Set-Cookie}</td></tr>
 * </table>
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li><b>{@code /kakao/authorize}·{@code /kakao/callback}은 JSON을 반환하지 않는다. 항상 302다.</b>
 *       브라우저 네비게이션이라 {@code ApiResponse} 포맷이 사용자 화면에 노출되면 안 된다.</li>
 *   <li><b>실패도 예외를 던지지 않는다.</b> {@code {FE}/oauth/complete?error={code}}로 302해
 *       <b>FE가 에러 UI를 소유</b>하게 한다. A005·A006·A009가 이 경로로 전달된다.</li>
 *   <li>쿠키 값은 {@code CookieUtil}에서만 만든다.</li>
 * </ul>
 *
 * <p><b>{@code /authorize}</b> — state 생성 → {@code oauth_state} 쿠키 설정 → 카카오 authorize URL로 302.<br>
 * 카카오로 보낼 쿼리: {@code client_id}, {@code redirect_uri}(설정값), {@code response_type=code}, {@code state}
 *
 * <p><b>{@code /callback} 처리 순서</b>
 * <ol>
 *   <li>카카오가 {@code ?error=}를 실어 보낸 경우(동의 거부 등) → 그대로 FE로 302 {@code ?error=}</li>
 *   <li>{@code oauth_state} 쿠키와 쿼리 {@code state} 대조 → 불일치·누락이면 {@code INVALID_OAUTH_STATE}(A009)</li>
 *   <li><b>{@code oauth_state} 쿠키 즉시 삭제</b>(1회용). 뒤로가기로 콜백을 재사용해도 여기서 걸린다</li>
 *   <li>{@code authService.kakaoLogin(code)}</li>
 *   <li>refresh 원문을 {@code refresh_token} 쿠키로 설정</li>
 *   <li>{@code {app.frontend.base-url}/oauth/complete}로 302. 신규 가입이면 {@code ?isNewUser=true}</li>
 * </ol>
 *
 * <p><b>주의</b>
 * <ul>
 *   <li><b>응답 {@code data}에 {@code refreshToken}을 절대 포함하지 않는다</b> — 오직 {@code Set-Cookie}로만.</li>
 *   <li><b>어떤 응답에도 access token을 URL·쿼리스트링에 싣지 않는다.</b></li>
 *   <li>state 대조는 {@code MessageDigest.isEqual}로 <b>상수 시간 비교</b>한다.</li>
 *   <li>⚠️ 카카오로 보내는 {@code redirect_uri}는 <b>percent-encoding이 필요하다.</b>
 *       {@code UriComponentsBuilder.encode()}는 query 값의 {@code :}·{@code /}를 RFC 3986상 허용 문자로 보고
 *       남겨두므로, {@code URLEncoder}로 직접 인코딩해야 카카오 등록값과 정확히 대조된다.</li>
 *   <li>{@code isNewUser}는 자격증명이 아니라 "추가 정보 입력" UX 용도이므로 query param으로 안전하다(게이팅 아님).</li>
 *   <li>재발급 응답에는 <b>{@code Set-Cookie}가 없다</b>(rotation 미적용).</li>
 * </ul>
 */
public class AuthController {
}
