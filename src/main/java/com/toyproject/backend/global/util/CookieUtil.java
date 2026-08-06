package com.toyproject.backend.global.util;

/**
 * [8단계 · K] 인증 쿠키 팩토리 — 프로토타입 (구현 전)
 *
 * <p><b>역할</b><br>
 * {@code refresh_token}·{@code oauth_state} 두 쿠키의 생성/삭제를 한곳에 모은다.
 * 속성 하나만 어긋나도 로그인 전체가 깨지므로 컨트롤러에 흩뿌리지 않는다.
 *
 * <p><b>개발 방향</b>
 * <ul>
 *   <li>{@code ResponseCookie}를 반환하는 정적 팩터리 유틸. 상태가 없으므로 Bean으로 만들지 않는다.</li>
 *   <li>공통 속성은 private 빌더 헬퍼로 묶는다.</li>
 * </ul>
 *
 * <p><b>쿠키 2종</b> — 둘 다 {@code HttpOnly} + {@code Secure} + {@code Path=/api/auth} +
 * {@code Domain} 미설정(host-only). <b>{@code SameSite}만 다르다.</b>
 * <table border="1">
 *   <tr><th></th><th>{@code refresh_token}</th><th>{@code oauth_state}</th></tr>
 *   <tr><td>값</td><td>불투명 랜덤 원문(base64url 43자)</td><td>랜덤 state</td></tr>
 *   <tr><td>{@code SameSite}</td><td>{@code Strict}</td><td><b>{@code Lax}</b></td></tr>
 *   <tr><td>{@code Max-Age}</td><td>7일({@code jwt.refresh-expiration})</td><td><b>5분</b></td></tr>
 *   <tr><td>수명</td><td>로그아웃·탈퇴까지</td><td>콜백에서 1회 사용 후 즉시 삭제</td></tr>
 * </table>
 *
 * <p><b>예정 메서드</b>: {@code refreshTokenCookie(raw, maxAge)}, {@code deleteRefreshTokenCookie()},
 * {@code oauthStateCookie(state)}, {@code deleteOauthStateCookie()} + 쿠키 이름 상수
 *
 * <p><b>주의</b>
 * <ul>
 *   <li>⚠️ <b>{@code SameSite}를 둘 다 {@code Strict}로 맞추면 로그인이 전부 실패한다.</b>
 *       콜백은 {@code kauth.kakao.com} → API 호스트로의 <b>cross-site top-level GET 네비게이션</b>인데
 *       {@code Strict} 쿠키는 여기서 전송되지 않는다. {@code Lax}는 top-level GET에 한해 전송되므로
 *       {@code oauth_state}는 반드시 {@code Lax}여야 한다.</li>
 *   <li>{@code refresh_token}은 FE→API의 <b>same-site XHR</b>에만 쓰이므로 {@code Strict}가 맞다.
 *       콜백 응답에서 이 쿠키를 <i>심는 것</i>은 SameSite와 무관하게 항상 동작한다
 *       — SameSite는 브라우저가 쿠키를 <i>보낼 때</i>만 관여한다.</li>
 *   <li>⚠️ <b>삭제 시에도 {@code Path=/api/auth}를 반드시 함께 지정</b>한다.
 *       {@code Max-Age=0}만 내리면 Path가 {@code /}로 해석돼 매칭되지 않아 쿠키가 지워지지 않는다.</li>
 *   <li>{@code Secure}는 로컬 개발에도 문제없다(브라우저가 {@code http://localhost}를 secure context로 취급).</li>
 * </ul>
 */
public class CookieUtil {
}
