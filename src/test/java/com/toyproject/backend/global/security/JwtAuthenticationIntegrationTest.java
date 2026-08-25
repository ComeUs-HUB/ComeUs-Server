package com.toyproject.backend.global.security;

import com.toyproject.backend.domain.auth.config.JwtProperties;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [6단계 · H·J] 인증 필터 체인 검증
 *
 * <p><b>이 단계의 완료 조건이 그대로 테스트 목록이다</b> — 미인증 401(A001), 위조 A003 / 만료 A004 /
 * 탈퇴 <b>A008(403)</b> 구분. 문서의 순서 결정 원칙 2("각 단계는 그 단계에서 검증이 끝나야 한다")대로,
 * <b>토큰 발급 API(7·8단계) 없이</b> 검증한다 — 테스트가 {@code JwtProvider}로 토큰을 직접 만들어 넣는다.
 *
 * <p>{@code /api/users/me}는 아직 컨트롤러가 없지만(9단계), <b>보안 필터는 디스패치보다 먼저 돈다</b>.
 * 거절 경로는 핸들러 유무와 무관하게 그대로 재현되므로 이 시점에 검증이 성립한다.
 * 반대로 인증에 <b>성공</b>했는지는 "401/403이 아님"으로만 확인할 수 있다(핸들러가 없어 404가 된다).
 *
 * <p>{@code @Transactional} — 각 테스트가 저장한 회원을 롤백한다. MockMvc는 같은 스레드에서 돌아
 * 필터의 DB 조회가 테스트 트랜잭션을 그대로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("인증 필터 체인 (6단계)")
class JwtAuthenticationIntegrationTest {

    /** 인증이 필요한 경로. 핸들러는 9단계에서 생긴다. */
    private static final String PROTECTED_URI = "/api/users/me";

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private User saveActiveUser() {
        return userRepository.save(User.ofKakao("kakao-6001", "active@kakao.com", "민준"));
    }

    private User saveWithdrawnUser() {
        User user = userRepository.save(User.ofKakao("kakao-6002", "withdrawn@kakao.com", "탈퇴자"));
        user.withdraw();
        return user;
    }

    /** 서명은 정상이지만 <b>이미 만료된</b> 토큰. 만료를 음수로 준 provider로 만든다(30분을 기다릴 수 없다). */
    private String expiredTokenFor(User user) {
        JwtProperties expiredProps = new JwtProperties(
                jwtProperties.secret(), -1_000L, jwtProperties.refreshExpiration());
        return new JwtProvider(expiredProps).createAccessToken(user);
    }

    /** 우리 키가 아닌 키로 서명한 토큰. 형식은 완전한 JWT다. */
    private String forgedTokenFor(User user) {
        JwtProperties forgedProps = new JwtProperties(
                "forged-secret-key-32bytes-or-longer!!!!!", jwtProperties.accessExpiration(), jwtProperties.refreshExpiration());
        return new JwtProvider(forgedProps).createAccessToken(user);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Nested
    @DisplayName("거절 — EntryPoint가 사유별로 상태 코드를 나눈다")
    class Rejection {

        @Test
        @DisplayName("토큰 없이 보호 자원 접근 → 401 A001, 공통 ApiResponse 포맷")
        void noToken() throws Exception {
            mockMvc.perform(get(PROTECTED_URI))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("A001"))
                    // 필터 단계라 GlobalExceptionHandler를 타지 않는데도 같은 포맷이어야 한다.
                    .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
        }

        @Test
        @DisplayName("위조 서명 토큰 → 401 A003 (만료로 뭉개지지 않는다)")
        void forgedToken() throws Exception {
            String forged = forgedTokenFor(saveActiveUser());

            mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A003"));
        }

        @Test
        @DisplayName("만료 토큰 → 401 A004 (위조로 뭉개지지 않는다)")
        void expiredToken() throws Exception {
            String expired = expiredTokenFor(saveActiveUser());

            mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A004"));
        }

        @Test
        @DisplayName("탈퇴 회원의 유효한 토큰 → 403 A008 — A001로 뭉개지면 죽은 코드가 된다")
        void withdrawnUser() throws Exception {
            String token = jwtProvider.createAccessToken(saveWithdrawnUser());

            mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    // ⚠️ 이 단계의 핵심. EntryPoint가 401을 하드코딩하면 여기서 깨진다.
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("A008"))
                    .andExpect(jsonPath("$.error.message").value("탈퇴한 회원입니다."));
        }

        @Test
        @DisplayName("JWT 형식이 아닌 Bearer 값 → 401 A003")
        void malformedToken() throws Exception {
            mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A003"));
        }

        @Test
        @DisplayName("삭제된 회원의 서명 유효 토큰 → 401 A001 (탈퇴와 구분된다)")
        void unknownUser() throws Exception {
            User user = saveActiveUser();
            String token = jwtProvider.createAccessToken(user);
            userRepository.delete(user);
            userRepository.flush();

            mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A001"));
        }

        @Test
        @DisplayName("한글 메시지가 깨지지 않는다 (charset=UTF-8)")
        void utf8Charset() throws Exception {
            var response = mockMvc.perform(get(PROTECTED_URI)).andReturn().getResponse();

            assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase(StandardCharsets.UTF_8.name());
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("인증이 필요합니다.");
        }
    }

    @Nested
    @DisplayName("통과 — 필터가 막지 않아야 하는 경로")
    class PassThrough {

        @Test
        @DisplayName("ACTIVE 회원의 유효한 토큰은 인증을 통과한다 (핸들러가 없어 404)")
        void activeUser() throws Exception {
            String token = jwtProvider.createAccessToken(saveActiveUser());

            int status = mockMvc.perform(get(PROTECTED_URI).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andReturn().getResponse().getStatus();

            // 9단계에서 컨트롤러가 생기면 200이 된다. 지금 확인할 것은 '인증 거절이 아님'이다.
            assertThat(status).isNotIn(401, 403);
        }

        @Test
        @DisplayName("permitAll 경로는 필터가 막지 않는다 — 거절이 있다면 필터가 아니라 핸들러의 판단이어야 한다")
        void permitAllEndpoints() throws Exception {
            // 8단계에서 /refresh에 핸들러가 생겨, 쿠키 없는 요청은 이제 A007(401)로 거절된다.
            // 그래서 "401이 아님"으로는 더 볼 수 없다 — 401의 '사유'가 필터(A001)가 아닌지를 본다.
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(jsonPath("$.error.code").value("A007"));

            // 인가 시작은 카카오로 302된다. 여기서 401/403이 나오면 로그인 자체가 불가능해진다.
            mockMvc.perform(get("/api/auth/kakao/authorize"))
                    .andExpect(status().isFound());
        }
    }

    @Nested
    @DisplayName("CORS — 쿠키 왕복 때문에 allowCredentials가 필수다")
    class Cors {

        @Test
        @DisplayName("허용 origin의 preflight는 통과하고 credentials를 허용한다")
        void allowedOrigin() throws Exception {
            mockMvc.perform(options("/api/auth/refresh")
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                    // 이 헤더가 없으면 FE의 credentials:'include' 요청이 브라우저에서 거부된다.
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        @DisplayName("허용 목록에 없는 origin의 preflight는 차단된다")
        void disallowedOrigin() throws Exception {
            mockMvc.perform(options("/api/auth/refresh")
                            .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        @DisplayName("보호 자원의 preflight도 인증 없이 통과한다 — preflight엔 Authorization 헤더가 실리지 않는다")
        void preflightOnProtectedResource() throws Exception {
            mockMvc.perform(options(PROTECTED_URI)
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk());
        }
    }
}
