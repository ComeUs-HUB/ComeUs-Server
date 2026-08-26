package com.toyproject.backend.domain.auth.controller;

import com.toyproject.backend.domain.auth.dto.KakaoLoginResult;
import com.toyproject.backend.domain.auth.dto.TokenResponse;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.service.AuthService;
import com.toyproject.backend.global.exception.BusinessException;
import com.toyproject.backend.global.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [8단계 · K] {@link AuthController} 검증
 *
 * <p>8단계의 완료 조건은 <b>브라우저 전체 플로우</b>라 여기서 대신할 수 없다(카카오 왕복이 필요하다).
 * 이 테스트가 고정하는 것은 <b>브라우저에 무엇을 내보내는가</b>다 — 상태 코드, {@code Location},
 * {@code Set-Cookie} 속성. 브라우저에서 깨졌을 때 원인 후보를 줄이는 게 목적이다.
 *
 * <p>{@code AuthService}는 {@code @MockitoBean}으로 대체한다. 서비스 로직은 7단계에서 이미 검증했고,
 * 여기서 관심 있는 건 <b>HTTP 계약</b>뿐이다. 그 덕에 카카오 왕복 없이 콜백 분기를 전부 재현할 수 있다.
 *
 * <p>{@code kakao.*}·{@code app.*} 값은 {@code src/test/resources/application.yml}에서 온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인증 API (8단계)")
class AuthControllerTest {

    private static final String AUTHORIZE_URI = "/api/auth/kakao/authorize";
    private static final String CALLBACK_URI = "/api/auth/kakao/callback";
    private static final String FRONTEND_COMPLETE = "http://localhost:3000/oauth/complete";

    private static final String STATE = "Rk9vQmFyU3RhdGVWYWx1ZUZvclRlc3RpbmcxMjM0NTY3";
    private static final String CODE = "kakao-authorization-code";
    private static final String RAW_REFRESH_TOKEN = "0KDf9YfQmXo1kSxq7t2ZLPu8bWnErAvC3hJd5gTy6Mk";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private static String location(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private static List<String> setCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    /** 이름이 같은 {@code Set-Cookie} 헤더 하나를 골라 온다. 삭제 쿠키도 같은 이름으로 내려간다. */
    private static String setCookie(MvcResult result, String name) {
        return setCookies(result).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 응답에 없다: " + setCookies(result)));
    }

    @Nested
    @DisplayName("GET /kakao/authorize — 로그인 시작")
    class Authorize {

        @Test
        @DisplayName("카카오 인가 URL로 302하고 같은 state를 쿠키로 심는다")
        void redirectsToKakaoWithState() throws Exception {
            given(authService.generateOAuthState()).willReturn(STATE);

            MvcResult result = mockMvc.perform(get(AUTHORIZE_URI))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result))
                    .startsWith("https://kauth.kakao.com/oauth/authorize?")
                    .contains("client_id=test-client-id")
                    .contains("response_type=code")
                    .contains("state=" + STATE);

            assertThat(setCookie(result, CookieUtil.OAUTH_STATE))
                    .as("쿠키의 state와 URL의 state가 달라지면 콜백에서 전부 A009가 된다")
                    .contains(STATE)
                    .contains("SameSite=Lax")
                    .contains("Path=/api/auth/kakao/callback")
                    .contains("HttpOnly");
        }

        @Test
        @DisplayName("redirect_uri는 percent-encoding한다 — 카카오 등록값과 문자열로 대조되기 때문")
        void percentEncodesRedirectUri() throws Exception {
            given(authService.generateOAuthState()).willReturn(STATE);

            MvcResult result = mockMvc.perform(get(AUTHORIZE_URI)).andReturn();

            assertThat(location(result))
                    .as("UriComponentsBuilder는 query 값의 : 와 / 를 남겨두므로 쓰지 않는다")
                    .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fauth%2Fkakao%2Fcallback");
        }
    }

    @Nested
    @DisplayName("GET /kakao/callback — 항상 302, 절대 JSON이 아니다")
    class Callback {

        @Test
        @DisplayName("로그인에 성공하면 FE 복귀 주소로 302하고 refresh 쿠키를 심는다")
        void redirectsToFrontendWithRefreshCookie() throws Exception {
            given(authService.kakaoLogin(CODE)).willReturn(new KakaoLoginResult(RAW_REFRESH_TOKEN, false));
            given(authService.getRefreshTokenMaxAge()).willReturn(Duration.ofDays(7));

            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result)).isEqualTo(FRONTEND_COMPLETE);
            assertThat(setCookie(result, CookieUtil.REFRESH_TOKEN))
                    .contains(RAW_REFRESH_TOKEN)
                    .contains("SameSite=Strict")
                    .contains("Path=/api/auth")
                    .contains("HttpOnly");
        }

        @Test
        @DisplayName("refresh 원문이 URL에 실리지 않는다 — 오직 Set-Cookie로만 나간다")
        void neverPutsRefreshTokenInUrl() throws Exception {
            given(authService.kakaoLogin(CODE)).willReturn(new KakaoLoginResult(RAW_REFRESH_TOKEN, true));
            given(authService.getRefreshTokenMaxAge()).willReturn(Duration.ofDays(7));

            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andReturn();

            assertThat(location(result)).doesNotContain(RAW_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("신규 가입이면 ?isNewUser=true를 붙인다")
        void marksNewUser() throws Exception {
            given(authService.kakaoLogin(CODE)).willReturn(new KakaoLoginResult(RAW_REFRESH_TOKEN, true));
            given(authService.getRefreshTokenMaxAge()).willReturn(Duration.ofDays(7));

            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andReturn();

            assertThat(location(result)).isEqualTo(FRONTEND_COMPLETE + "?isNewUser=true");
        }

        @Test
        @DisplayName("state가 불일치하면 A009로 FE에 넘기고 카카오를 부르지 않는다")
        void rejectsMismatchedState() throws Exception {
            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, "different-state-value")))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result)).isEqualTo(FRONTEND_COMPLETE + "?error=A009");
            then(authService).should(never()).kakaoLogin(anyString());
        }

        @Test
        @DisplayName("oauth_state 쿠키가 없으면 A009 — SameSite 오설정의 회귀 방지")
        void rejectsMissingStateCookie() throws Exception {
            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE))
                    .andReturn();

            assertThat(location(result))
                    .as("Strict로 잘못 두면 쿠키가 안 실려와 실제 브라우저에서 이 경로를 탄다")
                    .isEqualTo(FRONTEND_COMPLETE + "?error=A009");
        }

        @Test
        @DisplayName("state를 소비한 뒤 oauth_state 쿠키를 즉시 만료시킨다 — 1회용")
        void alwaysClearsStateCookie() throws Exception {
            given(authService.kakaoLogin(CODE)).willReturn(new KakaoLoginResult(RAW_REFRESH_TOKEN, false));
            given(authService.getRefreshTokenMaxAge()).willReturn(Duration.ofDays(7));

            MvcResult success = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andReturn();
            MvcResult failure = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE))
                    .andReturn();

            assertThat(setCookie(success, CookieUtil.OAUTH_STATE))
                    .as("남겨두면 뒤로가기로 콜백을 재사용할 수 있다")
                    .contains("Max-Age=0")
                    .contains("Path=/api/auth/kakao/callback");
            assertThat(setCookie(failure, CookieUtil.OAUTH_STATE))
                    .as("실패한 state를 남겨둘 이유가 없다")
                    .contains("Max-Age=0");
        }

        @Test
        @DisplayName("사용자가 동의를 거부하면 카카오의 error를 그대로 FE에 넘긴다")
        void forwardsKakaoError() throws Exception {
            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("error", "access_denied")
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result)).isEqualTo(FRONTEND_COMPLETE + "?error=access_denied");
            then(authService).should(never()).kakaoLogin(anyString());
        }

        @Test
        @DisplayName("error 값의 &가 인코딩된다 — 인코딩하지 않으면 FE URL에 파라미터가 주입된다")
        void encodesAmpersandInErrorValue() throws Exception {
            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("error", "x&isNewUser=true")
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result))
                    .as("콜백은 permitAll이라 error 값을 누구나 지정할 수 있다. "
                            + "인코딩하지 않으면 &가 구분자로 붙어 isNewUser=true가 주입된다")
                    .isEqualTo(FRONTEND_COMPLETE + "?error=x%26isNewUser%3Dtrue");
        }

        @ParameterizedTest(name = "error={0}")
        @ValueSource(strings = {"a b", "{x}", "100%", "a#b", "가"})
        @DisplayName("URI에 못 쓰는 문자가 와도 302다 — URI.create가 터지면 JSON 500이 주소창에 뜬다")
        void encodesIllegalUriCharacters(String malicious) throws Exception {
            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("error", malicious)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result))
                    .as("이 redirect는 try 블록 밖이라 예외가 GlobalExceptionHandler까지 올라간다")
                    .startsWith(FRONTEND_COMPLETE + "?error=")
                    .doesNotContain(" ", "{", "}");
        }

        @Test
        @DisplayName("카카오 연동이 실패해도 502가 아니라 302다 — 에러 화면은 FE가 소유한다")
        void convertsBusinessExceptionToRedirect() throws Exception {
            willThrow(new BusinessException(AuthErrorCode.KAKAO_API_ERROR))
                    .given(authService).kakaoLogin(CODE);

            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result))
                    .as("ApiResponse JSON이 브라우저 주소창에 노출되면 안 된다")
                    .isEqualTo(FRONTEND_COMPLETE + "?error=A006");
        }
        @Test
        @DisplayName("DB 장애처럼 예상 밖의 오류도 JSON 500이 아니라 302 ?error=C003이다")
        void convertsUnexpectedExceptionToRedirect() throws Exception {
            willThrow(new DataAccessResourceFailureException("커넥션 타임아웃"))
                    .given(authService).kakaoLogin(CODE);

            MvcResult result = mockMvc.perform(get(CALLBACK_URI)
                            .param("code", CODE)
                            .param("state", STATE)
                            .cookie(new Cookie(CookieUtil.OAUTH_STATE, STATE)))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(location(result))
                    .as("BusinessException만 잡으면 GlobalExceptionHandler까지 올라가 "
                            + "ApiResponse JSON 500이 브라우저 주소창에 렌더링된다")
                    .isEqualTo(FRONTEND_COMPLETE + "?error=C003");
        }
    }

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("accessToken을 반환하고 Set-Cookie는 내리지 않는다 — rotation 미적용")
        void returnsAccessTokenWithoutSetCookie() throws Exception {
            given(authService.reissue(RAW_REFRESH_TOKEN)).willReturn(TokenResponse.of("access.jwt", 1_800L));

            MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie(CookieUtil.REFRESH_TOKEN, RAW_REFRESH_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access.jwt"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1_800))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andReturn();

            assertThat(setCookies(result))
                    .as("여러 탭이 동시에 호출해도 모두 성공해야 한다")
                    .isEmpty();
        }

        @Test
        @DisplayName("쿠키가 없으면 서비스에 null이 넘어가 A007(401)이 된다")
        void rejectsMissingCookie() throws Exception {
            willThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND))
                    .given(authService).reissue(null);

            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("A007"));
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("토큰을 무효화하고 refresh 쿠키를 만료시킨다")
        void revokesAndClearsCookie() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/logout")
                            .cookie(new Cookie(CookieUtil.REFRESH_TOKEN, RAW_REFRESH_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            then(authService).should().logout(RAW_REFRESH_TOKEN);
            assertThat(setCookie(result, CookieUtil.REFRESH_TOKEN))
                    .as("Path가 빠지면 브라우저에서 쿠키가 실제로 지워지지 않는다")
                    .contains("Max-Age=0")
                    .contains("Path=/api/auth");
        }

        @Test
        @DisplayName("쿠키가 없어도 200이다 — 로그아웃은 멱등하다")
        void succeedsWithoutCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            then(authService).should().logout(null);
        }
    }
}
