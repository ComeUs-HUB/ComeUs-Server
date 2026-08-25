package com.toyproject.backend.global.util;

import com.toyproject.backend.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * [8단계 · K] 인증 쿠키 팩토리
 *
 * <p><b>역할</b><br>
 * {@code refresh_token}·{@code oauth_state} 두 쿠키의 생성/삭제를 한곳에 모은다.
 * 속성 하나만 어긋나도 로그인 전체가 깨지므로 컨트롤러에 흩뿌리지 않는다.
 *
 * <p><b>설계</b>
 * <ul>
 *   <li>{@code ResponseCookie}를 반환하는 팩터리. <b>원래는 상태가 없어 정적 유틸이었지만</b>
 *       {@code Secure}를 환경별로 나눠야 해서({@link AppProperties.Cookie} 참고) 빈으로 바꿨다.
 *       쿠키 <b>이름 상수는 {@code static}으로 남긴다</b> — {@code @CookieValue}의 속성이라
 *       컴파일 타임 상수여야 한다.</li>
 *   <li>공통 속성은 {@link #base}에 모으고, 각 팩터리는 이름·값·수명·{@code SameSite}만 정한다.</li>
 *   <li>삭제도 같은 헬퍼를 통과시킨다 — {@code Path}를 빠뜨릴 수 있는 경로를 아예 없앤다.</li>
 * </ul>
 *
 * <p><b>쿠키 2종</b> — 둘 다 {@code HttpOnly} + {@code Domain} 미설정(host-only)이고
 * {@code Secure}는 환경별 설정을 따른다. 나머지는 <b>전부 다르다.</b>
 * <table border="1">
 *   <tr><th></th><th>{@code refresh_token}</th><th>{@code oauth_state}</th></tr>
 *   <tr><td>값</td><td>불투명 랜덤 원문(base64url 43자)</td><td>랜덤 state</td></tr>
 *   <tr><td>{@code SameSite}</td><td>{@code Strict}</td><td><b>{@code Lax}</b></td></tr>
 *   <tr><td>{@code Path}</td><td>{@code /api/auth}</td><td><b>{@code /api/auth/kakao/callback}</b></td></tr>
 *   <tr><td>{@code Max-Age}</td><td>7일({@code jwt.refresh-expiration})</td><td><b>5분</b></td></tr>
 *   <tr><td>수명</td><td>로그아웃·탈퇴까지</td><td>콜백에서 1회 사용 후 즉시 삭제</td></tr>
 * </table>
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
 *   <li>⚠️ <b>삭제 시에도 발급할 때와 같은 {@code Path}를 지정</b>한다.
 *       {@code Max-Age=0}만 내리면 Path가 {@code /}로 해석돼 매칭되지 않아 쿠키가 지워지지 않는다.
 *       두 쿠키의 Path가 다르므로 각자 자기 경로로 내려야 한다 — {@link #base}가 강제한다.</li>
 *   <li>⚠️ <b>{@code Secure}는 로컬 개발에서 문제가 된다.</b> Chrome·Firefox는
 *       {@code http://localhost}를 신뢰 출처로 보고 허용하지만 <b>Safari는 거부</b>해
 *       {@code oauth_state}가 저장되지 않고 콜백이 전부 A009로 실패한다.
 *       그래서 {@code app.cookie.secure}로 분리했다(local {@code false} / prod {@code true}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    /** 재발급·로그아웃의 자격증명. 원문이 담긴다. */
    public static final String REFRESH_TOKEN = "refresh_token";

    /** 인가 왕복의 CSRF 방어값. 콜백에서 1회 대조 후 폐기된다. */
    public static final String OAUTH_STATE = "oauth_state";

    /**
     * {@code refresh_token}의 경로. 재발급·로그아웃·탈퇴가 모두 {@code /api/auth} 아래에 있다.
     *
     * <p>{@code /api/users/me} 같은 요청에 refresh token이 따라다닐 이유가 없고,
     * 실려 다닐수록 노출 지점만 늘어난다.
     */
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    /**
     * {@code oauth_state}의 경로. <b>쓰이는 곳이 콜백 하나뿐이라 거기까지 좁힌다.</b>
     *
     * <p>{@code /api/auth}로 두면 만료 전(5분) 동안 {@code /refresh}·{@code /logout} 요청에도
     * 실려 나간다 — 인가 왕복이 끝난 뒤에는 아무 쓸모가 없는 값이다.
     *
     * <p>쿠키 경로 매칭은 정확히 일치하거나 접두사여야 하는데, 여기서는 콜백 요청 경로와
     * <b>정확히 같아서</b> 그대로 전송된다.
     */
    private static final String OAUTH_STATE_PATH = "/api/auth/kakao/callback";

    /** 인가 왕복에만 필요한 값이라 짧게 잡는다. 사용자가 카카오 화면에 머무는 시간을 감안한 값이다. */
    private static final Duration OAUTH_STATE_MAX_AGE = Duration.ofMinutes(5);

    private final AppProperties appProperties;

    /**
     * @param rawRefreshToken 토큰 <b>원문</b>. 해시가 아니다 — DB에 저장하는 값과 다르다
     * @param maxAge          {@code AuthService.getRefreshTokenMaxAge()}. {@code RefreshToken.expiresAt}과
     *                        같은 설정값에서 나와야 쿠키는 살아 있는데 서버가 거절하는 상황이 안 생긴다
     */
    public ResponseCookie refreshTokenCookie(String rawRefreshToken, Duration maxAge) {
        return base(REFRESH_TOKEN, rawRefreshToken, REFRESH_TOKEN_PATH, maxAge, "Strict");
    }

    /** 로그아웃·탈퇴 시 브라우저에서 실제로 지워지도록 {@code Path}까지 맞춰 내린다. */
    public ResponseCookie deleteRefreshTokenCookie() {
        return base(REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO, "Strict");
    }

    public ResponseCookie oauthStateCookie(String state) {
        return base(OAUTH_STATE, state, OAUTH_STATE_PATH, OAUTH_STATE_MAX_AGE, "Lax");
    }

    /**
     * 콜백에서 state를 대조한 <b>직후</b> 내린다. 1회용이라 남겨두면 뒤로가기로 콜백을 재사용할 수 있다.
     *
     * <p>대조에 실패한 경우에도 함께 내린다 — 실패한 state를 남겨둘 이유가 없다.
     */
    public ResponseCookie deleteOauthStateCookie() {
        return base(OAUTH_STATE, "", OAUTH_STATE_PATH, Duration.ZERO, "Lax");
    }

    /**
     * 공통 속성을 적용한다. {@code domain}은 설정하지 않아 host-only 쿠키가 된다
     * — 상위 도메인에 걸면 다른 서브도메인에도 실려 나간다.
     */
    private ResponseCookie base(String name, String value, String path, Duration maxAge, String sameSite) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }
}
