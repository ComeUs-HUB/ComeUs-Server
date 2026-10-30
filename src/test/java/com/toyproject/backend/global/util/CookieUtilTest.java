package com.toyproject.backend.global.util;

import com.toyproject.backend.global.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [8단계 · K] {@link CookieUtil} 검증
 *
 * <p>속성 하나만 어긋나도 로그인 전체가 깨지는데, 그 실패는 <b>브라우저에서만</b> 드러나고
 * 원인 후보가 많다. 그래서 각 속성을 여기서 값으로 못박아 회귀를 막는다.
 *
 * <p>특히 세 가지가 핵심이다 — {@code SameSite}가 쿠키별로 다른지, <b>삭제 쿠키에도
 * {@code Path}가 붙는지</b>(빠뜨리면 로그아웃해도 브라우저에 쿠키가 남는다), 그리고
 * <b>{@code Secure} 기본값이 {@code true}인지</b>(운영에서 꺼지는 쪽으로 실패하면 안 된다).
 */
@DisplayName("CookieUtil")
class CookieUtilTest {

    private static final String RAW_TOKEN = "0KDf9YfQmXo1kSxq7t2ZLPu8bWnErAvC3hJd5gTy6Mk";
    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(7);
    private static final String REFRESH_TOKEN_PATH = "/api/auth";
    private static final String OAUTH_STATE_PATH = "/api/auth/kakao/callback";

    /** {@code app.frontend.base-url}은 필수다(ADR 0022). 쿠키와 무관해 더미를 넣는다. */
    private static final AppProperties.Frontend FRONTEND = new AppProperties.Frontend("http://localhost:3000");

    /** 운영 기본값. {@code app.cookie}가 통째로 없을 때와 같은 상태다. */
    private final CookieUtil cookieUtil = new CookieUtil(new AppProperties(FRONTEND, null, null));

    @Test
    @DisplayName("네 쿠키 모두 HttpOnly이고 Domain은 설정하지 않는다")
    void appliesSharedAttributes() {
        ResponseCookie[] cookies = {
                cookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE),
                cookieUtil.deleteRefreshTokenCookie(),
                cookieUtil.oauthStateCookie("state-value"),
                cookieUtil.deleteOauthStateCookie()
        };

        assertThat(cookies).allSatisfy(cookie -> {
            assertThat(cookie.isHttpOnly()).as("JS가 읽으면 XSS로 탈취된다").isTrue();
            assertThat(cookie.getDomain())
                    .as("Domain을 걸면 다른 서브도메인에도 쿠키가 실려 나간다")
                    .isNull();
        });
    }

    @Nested
    @DisplayName("refresh_token")
    class RefreshTokenCookie {

        @Test
        @DisplayName("SameSite=Strict이고 Max-Age는 인자로 받은 수명을 그대로 쓴다")
        void isStrictWithGivenMaxAge() {
            ResponseCookie cookie = cookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE);

            assertThat(cookie.getName()).isEqualTo("refresh_token");
            assertThat(cookie.getValue()).isEqualTo(RAW_TOKEN);
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
            assertThat(cookie.getPath()).isEqualTo(REFRESH_TOKEN_PATH);
            assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_MAX_AGE);
        }

        @Test
        @DisplayName("삭제 쿠키는 Max-Age=0에 빈 값이고 Path가 발급 때와 같다")
        void deletionExpiresImmediately() {
            ResponseCookie cookie = cookieUtil.deleteRefreshTokenCookie();

            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getPath())
                    .as("Path가 다르면 브라우저가 매칭하지 못해 쿠키가 지워지지 않는다")
                    .isEqualTo(REFRESH_TOKEN_PATH);
        }
    }

    @Nested
    @DisplayName("oauth_state")
    class OauthStateCookie {

        @Test
        @DisplayName("SameSite=Lax다 — Strict면 카카오 콜백에 쿠키가 실려오지 않아 로그인이 전부 실패한다")
        void mustBeLax() {
            assertThat(cookieUtil.oauthStateCookie("state-value").getSameSite())
                    .as("콜백은 kauth.kakao.com → API 호스트의 cross-site top-level GET 네비게이션이다")
                    .isEqualTo("Lax");
        }

        @Test
        @DisplayName("Max-Age는 5분 — 인가 왕복에만 필요하다")
        void livesFiveMinutes() {
            assertThat(cookieUtil.oauthStateCookie("state-value").getMaxAge())
                    .isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("Path가 콜백 하나로 좁혀져 있다 — /refresh·/logout에 실려 나가면 안 된다")
        void isScopedToCallbackOnly() {
            assertThat(cookieUtil.oauthStateCookie("state-value").getPath())
                    .as("인가 왕복이 끝나면 아무 쓸모가 없는 값이다")
                    .isEqualTo(OAUTH_STATE_PATH);
        }

        @Test
        @DisplayName("삭제 쿠키도 Lax와 같은 Path를 유지한 채 Max-Age=0이다")
        void deletionKeepsLax() {
            ResponseCookie cookie = cookieUtil.deleteOauthStateCookie();

            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getPath()).isEqualTo(OAUTH_STATE_PATH);
        }
    }

    @Nested
    @DisplayName("Secure — 환경별로 갈린다")
    class Secure {

        @Test
        @DisplayName("설정이 없으면 켜진다 — 빠뜨렸을 때 꺼지는 쪽으로 실패하면 안 된다")
        void defaultsToEnabled() {
            assertThat(cookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE).isSecure()).isTrue();
            assertThat(cookieUtil.oauthStateCookie("state-value").isSecure()).isTrue();
        }

        @Test
        @DisplayName("app.cookie 블록만 있고 secure가 없어도 켜진다 — Boolean 래퍼를 쓰는 이유")
        void defaultsToEnabledWhenOnlySectionPresent() {
            CookieUtil util = new CookieUtil(new AppProperties(FRONTEND, null, new AppProperties.Cookie(null)));

            assertThat(util.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE).isSecure())
                    .as("primitive boolean이면 여기서 조용히 false가 된다")
                    .isTrue();
        }

        @Test
        @DisplayName("명시적으로 끄면 꺼진다 — 로컬이 평문 HTTP라 Safari가 Secure 쿠키를 거부한다")
        void turnsOffWhenExplicitlyDisabled() {
            CookieUtil util = new CookieUtil(new AppProperties(FRONTEND, null, new AppProperties.Cookie(false)));

            assertThat(util.oauthStateCookie("state-value").isSecure())
                    .as("Safari는 http://localhost에서 Secure 쿠키를 저장하지 않아 콜백이 전부 A009가 된다")
                    .isFalse();
            assertThat(util.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE).isSecure()).isFalse();
        }
    }

    @Test
    @DisplayName("두 쿠키의 SameSite와 Path는 서로 달라야 한다 — 같게 맞추면 로그인이 깨지거나 범위가 넓어진다")
    void policiesMustDiffer() {
        ResponseCookie refresh = cookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE);
        ResponseCookie state = cookieUtil.oauthStateCookie("state-value");

        assertThat(refresh.getSameSite()).isNotEqualTo(state.getSameSite());
        assertThat(refresh.getPath()).isNotEqualTo(state.getPath());
    }
}
