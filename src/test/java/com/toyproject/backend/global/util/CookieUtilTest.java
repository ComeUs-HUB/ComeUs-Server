package com.toyproject.backend.global.util;

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
 * <p>특히 두 가지가 핵심이다 — {@code SameSite}가 쿠키별로 다른지, 그리고 <b>삭제 쿠키에도
 * {@code Path}가 붙는지.</b> 후자를 빠뜨리면 로그아웃해도 브라우저에 쿠키가 그대로 남는다.
 */
@DisplayName("CookieUtil")
class CookieUtilTest {

    private static final String RAW_TOKEN = "0KDf9YfQmXo1kSxq7t2ZLPu8bWnErAvC3hJd5gTy6Mk";
    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(7);
    private static final String EXPECTED_PATH = "/api/auth";

    @Test
    @DisplayName("네 쿠키 모두 HttpOnly·Secure·Path=/api/auth이고 Domain은 설정하지 않는다")
    void appliesSharedAttributes() {
        ResponseCookie[] cookies = {
                CookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE),
                CookieUtil.deleteRefreshTokenCookie(),
                CookieUtil.oauthStateCookie("state-value"),
                CookieUtil.deleteOauthStateCookie()
        };

        assertThat(cookies).allSatisfy(cookie -> {
            assertThat(cookie.isHttpOnly()).as("JS가 읽으면 XSS로 탈취된다").isTrue();
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getPath())
                    .as("삭제 쿠키까지 Path가 같아야 브라우저가 매칭해 실제로 지운다")
                    .isEqualTo(EXPECTED_PATH);
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
            ResponseCookie cookie = CookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE);

            assertThat(cookie.getName()).isEqualTo("refresh_token");
            assertThat(cookie.getValue()).isEqualTo(RAW_TOKEN);
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
            assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_MAX_AGE);
        }

        @Test
        @DisplayName("삭제 쿠키는 Max-Age=0에 빈 값이다")
        void deletionExpiresImmediately() {
            ResponseCookie cookie = CookieUtil.deleteRefreshTokenCookie();

            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("oauth_state")
    class OauthStateCookie {

        @Test
        @DisplayName("SameSite=Lax다 — Strict면 카카오 콜백에 쿠키가 실려오지 않아 로그인이 전부 실패한다")
        void mustBeLax() {
            assertThat(CookieUtil.oauthStateCookie("state-value").getSameSite())
                    .as("콜백은 kauth.kakao.com → API 호스트의 cross-site top-level GET 네비게이션이다")
                    .isEqualTo("Lax");
        }

        @Test
        @DisplayName("Max-Age는 5분 — 인가 왕복에만 필요하다")
        void livesFiveMinutes() {
            assertThat(CookieUtil.oauthStateCookie("state-value").getMaxAge())
                    .isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("삭제 쿠키도 Lax를 유지한 채 Max-Age=0이다")
        void deletionKeepsLax() {
            ResponseCookie cookie = CookieUtil.deleteOauthStateCookie();

            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.getMaxAge()).isZero();
        }
    }

    @Test
    @DisplayName("두 쿠키의 SameSite는 서로 달라야 한다 — 같게 맞추면 로그인이 깨진다")
    void sameSitePoliciesMustDiffer() {
        assertThat(CookieUtil.refreshTokenCookie(RAW_TOKEN, REFRESH_MAX_AGE).getSameSite())
                .isNotEqualTo(CookieUtil.oauthStateCookie("state-value").getSameSite());
    }
}
