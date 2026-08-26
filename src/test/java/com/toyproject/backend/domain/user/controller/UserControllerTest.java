package com.toyproject.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.auth.client.KakaoOAuthClient;
import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.repository.RefreshTokenRepository;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.user.dto.KakaoUserResolution;
import com.toyproject.backend.domain.user.dto.UserUpdateRequest;
import com.toyproject.backend.domain.user.entity.Provider;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.entity.UserStatus;
import com.toyproject.backend.domain.user.repository.UserRepository;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [9단계 · K] 회원 API 검증
 *
 * <p><b>이 단계의 완료 조건이 그대로 테스트 목록이다</b> — access token으로 {@code /api/users/me}
 * GET·PUT·DELETE, 그리고 탈퇴 후 재로그인 재활성화.
 *
 * <p>서비스를 mock으로 대체하지 않는다. 8단계 {@code AuthControllerTest}는 HTTP 계약만 보면 됐지만,
 * 여기서는 <b>탈퇴 오케스트레이션의 순서와 결과</b>가 검증 대상이라 실제 서비스·DB가 필요하다.
 * 외부 경계인 {@code KakaoOAuthClient}만 대체해 카카오 호출을 막는다.
 *
 * <p>토큰은 {@code JwtProvider}로 직접 만든다 — 8단계 로그인 왕복을 다시 태울 이유가 없고,
 * 그래야 이 테스트가 카카오와 무관하게 돈다.
 *
 * <p>{@code @Transactional} — 각 테스트가 저장한 회원·토큰을 롤백한다. 탈퇴 흐름의 두 트랜잭션이
 * 테스트 트랜잭션에 참여하므로 <b>원자성 자체는 여기서 검증되지 않는다</b>(그건 설계상 원자적이지
 * 않기도 하다). 여기서 보는 것은 <b>순서와 최종 상태</b>다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("회원 API (9단계)")
class UserControllerTest {

    private static final String ME_URI = "/api/users/me";
    private static final String PROVIDER_ID = "kakao-9001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProvider jwtProvider;

    /** 탈퇴가 카카오로 실제 요청을 보내지 않게 막는다. */
    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.ofKakao(PROVIDER_ID, "user@kakao.com", "민준"));
        user.updateProfile(null, "010-1111-2222", "서울시 강남구", LocalDate.of(1999, 3, 1));
        refreshTokenRepository.save(
                RefreshToken.issue(user, "a".repeat(64), LocalDateTime.now().plusDays(7)));
        bearer = "Bearer " + jwtProvider.createAccessToken(user);
    }

    private String json(UserUpdateRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMyInfo {

        @Test
        @DisplayName("내 정보를 반환한다")
        void returnsMyInfo() throws Exception {
            mockMvc.perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(user.getId()))
                    .andExpect(jsonPath("$.data.email").value("user@kakao.com"))
                    .andExpect(jsonPath("$.data.name").value("민준"))
                    .andExpect(jsonPath("$.data.phone").value("010-1111-2222"))
                    .andExpect(jsonPath("$.data.role").value("USER"))
                    .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                    .andExpect(jsonPath("$.data.createdAt").exists());
        }

        @Test
        @DisplayName("providerId·status·withdrawnAt은 응답에 없다")
        void hidesInternalFields() throws Exception {
            mockMvc.perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(jsonPath("$.data.providerId").doesNotExist())
                    .andExpect(jsonPath("$.data.status").doesNotExist())
                    .andExpect(jsonPath("$.data.withdrawnAt").doesNotExist());
        }

        @Test
        @DisplayName("토큰이 없으면 A001(401)")
        void rejectsAnonymous() throws Exception {
            mockMvc.perform(get(ME_URI))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("A001"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me — 부분 수정")
    class UpdateMyInfo {

        @Test
        @DisplayName("보낸 필드만 바뀌고 나머지는 유지된다")
        void updatesOnlyProvidedFields() throws Exception {
            mockMvc.perform(put(ME_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UserUpdateRequest("지훈", null, null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("지훈"))
                    .andExpect(jsonPath("$.data.phone").value("010-1111-2222"))
                    .andExpect(jsonPath("$.data.address").value("서울시 강남구"));
        }

        @Test
        @DisplayName("빈 바디도 유효하다 — 모든 필드가 optional이다")
        void emptyBodyIsValid() throws Exception {
            mockMvc.perform(put(ME_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("민준"));
        }

        @Test
        @DisplayName("휴대폰 형식이 틀리면 C001(400)과 위반 필드를 돌려준다")
        void rejectsMalformedPhone() throws Exception {
            mockMvc.perform(put(ME_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UserUpdateRequest(null, "01012", null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("phone"));
        }

        @Test
        @DisplayName("생년월일이 미래면 C001 — @Past는 값이 있을 때만 동작한다")
        void rejectsFutureBirthDate() throws Exception {
            mockMvc.perform(put(ME_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UserUpdateRequest(null, null, null, LocalDate.now().plusDays(1)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"))
                    .andExpect(jsonPath("$.error.errors[0].field").value("birthDate"));
        }

        @Test
        @DisplayName("이름이 컬럼 길이를 넘으면 C001 — DB가 C005로 거절하기 전에 막는다")
        void rejectsTooLongName() throws Exception {
            mockMvc.perform(put(ME_URI)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UserUpdateRequest("가".repeat(51), null, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("C001"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me — 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("소프트 삭제하고 토큰을 전수 삭제한 뒤 쿠키를 만료시킨다")
        void withdrawsAndClearsSession() throws Exception {
            MvcResult result = mockMvc.perform(delete(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                    .as("Path가 빠지면 브라우저에서 쿠키가 실제로 지워지지 않는다")
                    .contains("refresh_token=")
                    .contains("Max-Age=0")
                    .contains("Path=/api/auth");

            User withdrawn = userRepository.findById(user.getId()).orElseThrow();
            assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(withdrawn.getWithdrawnAt()).isNotNull();
            assertThat(refreshTokenRepository.findAll())
                    .as("탈퇴 회원의 세션이 남아 있으면 안 된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("카카오 연결 해제에 providerId를 넘긴다")
        void unlinksKakaoAccount() throws Exception {
            mockMvc.perform(delete(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());

            then(kakaoOAuthClient).should().unlink(PROVIDER_ID);
        }

        @Test
        @DisplayName("카카오 장애로 unlink가 실패해도 탈퇴는 성립한다 — best-effort")
        void survivesKakaoFailure() throws Exception {
            willThrow(new BusinessException(AuthErrorCode.KAKAO_API_ERROR))
                    .given(kakaoOAuthClient).unlink(anyString());

            mockMvc.perform(delete(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("탈퇴 후 같은 access token으로 접근하면 A008(403)")
        void rejectsWithdrawnUsersToken() throws Exception {
            mockMvc.perform(delete(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());

            mockMvc.perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("A008"));
        }

        @Test
        @DisplayName("탈퇴 후 같은 카카오 계정으로 재로그인하면 자동 재활성화된다")
        void reactivatesOnRelogin() throws Exception {
            mockMvc.perform(delete(ME_URI).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk());

            KakaoUserResolution resolution =
                    userService.findOrCreateKakaoUser(PROVIDER_ID, "user@kakao.com", "민준");

            assertThat(resolution.user().getId())
                    .as("새 회원을 만들지 않고 기존 회원을 되살려야 한다")
                    .isEqualTo(user.getId());
            assertThat(resolution.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(resolution.user().getProvider()).isEqualTo(Provider.KAKAO);
            assertThat(resolution.isNewUser())
                    .as("재활성화는 가입이 아니라 복귀다")
                    .isFalse();
        }
    }
}
