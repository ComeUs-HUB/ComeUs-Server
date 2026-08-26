package com.toyproject.backend.domain.auth.client;

import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.config.KakaoProperties;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * [4단계 · G] {@link KakaoOAuthClient} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 생성자 주입 컴포넌트라 직접 조립할 수 있고,
 * {@code KakaoProperties}가 record라 그냥 {@code new} 하면 된다. 컨텍스트가 없으므로
 * 테스트 yml에 {@code kakao.*}가 없다는 사실이 이 테스트에 영향을 주지 않는다.
 *
 * <p>이 단계의 완료 조건은 <b>카카오 실패가 경계에서 A005/A006으로 변환되는 것</b>이므로,
 * 예외 검증은 타입만 보지 않고 {@code getErrorCode()}까지 확인한다.
 * 실제 카카오 왕복은 8단계(브라우저 E2E)의 몫이다.
 */
@DisplayName("KakaoOAuthClient")
class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

    private static final KakaoProperties PROPERTIES = new KakaoProperties(
            "test-client-id",
            "test-client-secret",
            "test-admin-key",
            "http://localhost:8080/api/auth/kakao/callback",
            "https://kauth.kakao.com/oauth/authorize",
            TOKEN_URI,
            USER_INFO_URI,
            UNLINK_URI
    );

    private MockRestServiceServer kauthServer;
    private MockRestServiceServer kapiServer;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        // bindTo(builder) 이후에 build() 해야 목 요청 팩터리가 적용된다.
        RestClient.Builder kauthBuilder = RestClient.builder();
        kauthServer = MockRestServiceServer.bindTo(kauthBuilder).build();
        RestClient kauthRestClient = kauthBuilder.build();

        RestClient.Builder kapiBuilder = RestClient.builder();
        kapiServer = MockRestServiceServer.bindTo(kapiBuilder).build();
        RestClient kapiRestClient = kapiBuilder.build();

        client = new KakaoOAuthClient(kauthRestClient, kapiRestClient, PROPERTIES);
    }

    private static void assertErrorCode(Throwable thrown, AuthErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    @Nested
    @DisplayName("requestToken — 인가 코드를 카카오 토큰으로 교환")
    class RequestToken {

        @Test
        @DisplayName("성공 — form 5개 필드를 실어 보내고 accessToken을 매핑한다")
        void success() {
            MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
            expectedForm.add("grant_type", "authorization_code");
            expectedForm.add("client_id", "test-client-id");
            expectedForm.add("client_secret", "test-client-secret");
            expectedForm.add("redirect_uri", "http://localhost:8080/api/auth/kakao/callback");
            expectedForm.add("code", "auth-code-123");

            kauthServer.expect(requestTo(TOKEN_URI))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(content().formData(expectedForm))
                    .andRespond(withSuccess("""
                            {
                              "access_token": "kakao-access-token",
                              "token_type": "bearer",
                              "refresh_token": "kakao-refresh-token",
                              "expires_in": 21599,
                              "scope": "profile_nickname account_email"
                            }
                            """, MediaType.APPLICATION_JSON));

            KakaoTokenResponse response = client.requestToken("auth-code-123");

            assertThat(response.accessToken()).isEqualTo("kakao-access-token");
            assertThat(response.expiresIn()).isEqualTo(21599);
            kauthServer.verify();
        }

        @Test
        @DisplayName("4xx — 인가 코드 재사용 등 우리 요청 문제는 A005")
        void clientError() {
            kauthServer.expect(requestTo(TOKEN_URI))
                    .andRespond(withBadRequest()
                            .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            Throwable thrown = catchThrowable(() -> client.requestToken("used-code"));

            assertErrorCode(thrown, AuthErrorCode.KAKAO_AUTH_FAILED);
        }

        @Test
        @DisplayName("5xx — 카카오 장애는 A006")
        void serverError() {
            kauthServer.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

            Throwable thrown = catchThrowable(() -> client.requestToken("auth-code-123"));

            assertErrorCode(thrown, AuthErrorCode.KAKAO_API_ERROR);
        }
    }

    @Nested
    @DisplayName("fetchUserInfo — 카카오 사용자 정보 조회")
    class FetchUserInfo {

        @Test
        @DisplayName("성공 — Bearer 헤더를 싣고 id·email·nickname을 매핑한다")
        void success() {
            kapiServer.expect(requestTo(USER_INFO_URI))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                    .andRespond(withSuccess("""
                            {
                              "id": 123456789,
                              "kakao_account": {
                                "email": "user@kakao.com",
                                "profile": { "nickname": "민준" }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            KakaoUserInfoResponse response = client.fetchUserInfo("kakao-access-token");

            assertThat(response.providerId()).isEqualTo("123456789");
            assertThat(response.resolveEmail()).isEqualTo("user@kakao.com");
            assertThat(response.resolveNickname()).isEqualTo("민준");
            kapiServer.verify();
        }

        @Test
        @DisplayName("이메일 미동의 — email만 null이고 가입은 계속 진행할 수 있다")
        void emailNotAgreed() {
            kapiServer.expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess("""
                            {
                              "id": 123456789,
                              "kakao_account": {
                                "profile": { "nickname": "민준" }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            KakaoUserInfoResponse response = client.fetchUserInfo("kakao-access-token");

            assertThat(response.resolveEmail()).isNull();
            assertThat(response.resolveNickname()).isEqualTo("민준");
        }

        @Test
        @DisplayName("kakao_account가 통째로 없어도 NPE 없이 null을 반환한다")
        void kakaoAccountMissing() {
            kapiServer.expect(requestTo(USER_INFO_URI))
                    .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

            KakaoUserInfoResponse response = client.fetchUserInfo("kakao-access-token");

            // 실제 운영에서 가장 흔한 예외 상황이다. 여기서 NPE가 나면 로그인이 통째로 실패한다.
            assertThat(response.providerId()).isEqualTo("123456789");
            assertThat(response.resolveEmail()).isNull();
            assertThat(response.resolveNickname()).isNull();
        }

        @Test
        @DisplayName("4xx — 카카오 토큰 만료·무효는 A005")
        void clientError() {
            kapiServer.expect(requestTo(USER_INFO_URI))
                    .andRespond(withBadRequest()
                            .body("{\"code\":-401,\"msg\":\"this access token does not exist\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            Throwable thrown = catchThrowable(() -> client.fetchUserInfo("expired-token"));

            assertErrorCode(thrown, AuthErrorCode.KAKAO_AUTH_FAILED);
        }

        @Test
        @DisplayName("5xx — 카카오 장애는 A006")
        void serverError() {
            kapiServer.expect(requestTo(USER_INFO_URI)).andRespond(withServerError());

            Throwable thrown = catchThrowable(() -> client.fetchUserInfo("kakao-access-token"));

            assertErrorCode(thrown, AuthErrorCode.KAKAO_API_ERROR);
        }
    }

    @Nested
    @DisplayName("unlink — 카카오 연결 해제")
    class Unlink {

        @Test
        @DisplayName("성공 — Admin Key 헤더와 target_id form을 실어 보낸다")
        void success() {
            MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
            expectedForm.add("target_id_type", "user_id");
            expectedForm.add("target_id", "123456789");

            kapiServer.expect(requestTo(UNLINK_URI))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-admin-key"))
                    .andExpect(content().formData(expectedForm))
                    .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

            client.unlink("123456789");

            kapiServer.verify();
        }

        @Test
        @DisplayName("5xx — A006을 던진다 (삼키는 판단은 AuthService의 몫)")
        void serverError() {
            kapiServer.expect(requestTo(UNLINK_URI)).andRespond(withServerError());

            Throwable thrown = catchThrowable(() -> client.unlink("123456789"));

            assertErrorCode(thrown, AuthErrorCode.KAKAO_API_ERROR);
        }
    }
}
