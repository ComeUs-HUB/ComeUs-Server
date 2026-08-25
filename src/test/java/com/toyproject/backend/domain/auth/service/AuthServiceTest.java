package com.toyproject.backend.domain.auth.service;

import com.toyproject.backend.domain.auth.client.KakaoOAuthClient;
import com.toyproject.backend.domain.auth.client.dto.KakaoTokenResponse;
import com.toyproject.backend.domain.auth.client.dto.KakaoUserInfoResponse;
import com.toyproject.backend.domain.auth.dto.KakaoLoginResult;
import com.toyproject.backend.domain.auth.config.JwtProperties;
import com.toyproject.backend.domain.auth.dto.TokenResponse;
import com.toyproject.backend.domain.auth.entity.RefreshToken;
import com.toyproject.backend.domain.auth.exception.AuthErrorCode;
import com.toyproject.backend.domain.auth.repository.RefreshTokenRepository;
import com.toyproject.backend.domain.auth.token.JwtProvider;
import com.toyproject.backend.domain.auth.token.SecureTokenGenerator;
import com.toyproject.backend.domain.user.dto.KakaoUserResolution;
import com.toyproject.backend.domain.user.entity.User;
import com.toyproject.backend.domain.user.service.UserService;
import com.toyproject.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * [7단계 · I·K] {@link AuthService} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 완료 조건인 "재발급 유효성"과
 * "{@code logout} 멱등"은 <b>정책 판단</b>이라 DB 없이 드러난다. 실제 SQL(해시 조회·벌크 삭제·
 * {@code @EntityGraph})은 3단계 {@code RefreshTokenRepositoryTest}가 이미 검증했다.
 *
 * <p><b>{@link SecureTokenGenerator}는 mock이 아니라 실물을 쓴다.</b> 해시가 결정적이라 스텁보다 간단하고,
 * 무엇보다 "원문이 아니라 해시로 조회한다"는 것 자체를 테스트가 계산해 대조할 수 있다.
 * {@code JwtProperties}는 record라 그냥 {@code new} 하면 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String RAW_TOKEN = "0KDf9YfQmXo1kSxq7t2ZLPu8bWnErAvC3hJd5gTy6Mk";
    private static final String ACCESS_TOKEN = "signed.access.token";
    private static final long ACCESS_EXPIRATION_SECONDS = 1_800L;
    private static final long REFRESH_EXPIRATION_MILLIS = 604_800_000L;
    private static final String PROVIDER_ID = "kakao-1001";
    private static final Long USER_ID = 1L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private UserService userService;

    private final SecureTokenGenerator secureTokenGenerator = new SecureTokenGenerator();

    private final JwtProperties jwtProperties = new JwtProperties(
            "test-jwt-secret-for-unit-and-context-tests-only!",
            1_800_000L,
            REFRESH_EXPIRATION_MILLIS
    );

    private AuthService authService;
    private User user;
    private String tokenHash;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                refreshTokenRepository, secureTokenGenerator, jwtProvider, jwtProperties,
                kakaoOAuthClient, userService);
        user = User.ofKakao(PROVIDER_ID, "user@kakao.com", "민준");
        tokenHash = secureTokenGenerator.sha256Hex(RAW_TOKEN);
    }

    private RefreshToken issuedToken(LocalDateTime expiresAt) {
        return RefreshToken.issue(user, tokenHash, expiresAt);
    }

    @Nested
    @DisplayName("kakaoLogin — 로그인 본체")
    class KakaoLogin {

        private static final String CODE = "kakao-authorization-code";
        private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
        private static final String KAKAO_ID = "9001";
        private static final String KAKAO_EMAIL = "user@kakao.com";
        private static final String KAKAO_NICKNAME = "민준";

        private void givenKakaoRoundTrip() {
            given(kakaoOAuthClient.requestToken(CODE))
                    .willReturn(new KakaoTokenResponse(KAKAO_ACCESS_TOKEN, "bearer", null, 21_599, null));
            given(kakaoOAuthClient.fetchUserInfo(KAKAO_ACCESS_TOKEN))
                    .willReturn(new KakaoUserInfoResponse(
                            Long.valueOf(KAKAO_ID),
                            new KakaoUserInfoResponse.KakaoAccount(
                                    KAKAO_EMAIL, new KakaoUserInfoResponse.Profile(KAKAO_NICKNAME))));
        }

        @Test
        @DisplayName("카카오가 준 값을 user 도메인에 그대로 넘기고 refresh 원문을 돌려준다")
        void delegatesResolutionToUserDomain() {
            givenKakaoRoundTrip();
            given(userService.findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME))
                    .willReturn(KakaoUserResolution.existing(user));

            KakaoLoginResult result = authService.kakaoLogin(CODE);

            then(userService).should().findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME);
            assertThat(result.rawRefreshToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(result.isNewUser()).isFalse();
        }

        @Test
        @DisplayName("신규 가입 여부를 판단하지 않고 user 도메인의 결과를 그대로 전달한다")
        void propagatesNewUserFlag() {
            givenKakaoRoundTrip();
            given(userService.findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME))
                    .willReturn(KakaoUserResolution.created(user));

            assertThat(authService.kakaoLogin(CODE).isNewUser()).isTrue();
        }

        @Test
        @DisplayName("DB에는 원문이 아니라 SHA-256 해시가 저장된다")
        void storesHashNotRawToken() {
            givenKakaoRoundTrip();
            given(userService.findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME))
                    .willReturn(KakaoUserResolution.existing(user));

            KakaoLoginResult result = authService.kakaoLogin(CODE);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            then(refreshTokenRepository).should().save(captor.capture());
            RefreshToken saved = captor.getValue();

            assertThat(saved.getTokenHash())
                    .isEqualTo(secureTokenGenerator.sha256Hex(result.rawRefreshToken()))
                    .hasSize(64)
                    .isNotEqualTo(result.rawRefreshToken());
            assertThat(saved.getUser()).isSameAs(user);
        }

        @Test
        @DisplayName("expiresAt은 쿠키 Max-Age와 같은 설정값(7일)에서 나온다")
        void expiresAtFollowsConfiguredLifetime() {
            givenKakaoRoundTrip();
            given(userService.findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME))
                    .willReturn(KakaoUserResolution.existing(user));
            LocalDateTime expected = LocalDateTime.now().plus(authService.getRefreshTokenMaxAge());

            authService.kakaoLogin(CODE);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            then(refreshTokenRepository).should().save(captor.capture());
            assertThat(captor.getValue().getExpiresAt())
                    .as("쿠키는 살아 있는데 서버가 거절하는 상황을 막으려면 두 값이 같은 설정에서 나와야 한다")
                    .isCloseTo(expected, within(10, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("access token은 여기서 발급하지 않는다 — /refresh가 발급한다")
        void doesNotIssueAccessToken() {
            givenKakaoRoundTrip();
            given(userService.findOrCreateKakaoUser(KAKAO_ID, KAKAO_EMAIL, KAKAO_NICKNAME))
                    .willReturn(KakaoUserResolution.existing(user));

            authService.kakaoLogin(CODE);

            then(jwtProvider)
                    .shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("카카오 호출이 실패하면 회원도 토큰도 만들지 않고 예외가 그대로 전파된다")
        void abortsWhenKakaoFails() {
            willThrow(new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED))
                    .given(kakaoOAuthClient).requestToken(CODE);

            assertThatThrownBy(() -> authService.kakaoLogin(CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.KAKAO_AUTH_FAILED);

            then(userService).shouldHaveNoInteractions();
            then(refreshTokenRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("reissue — 유효성은 'row 존재 AND 미만료'다")
    class Reissue {

        @Test
        @DisplayName("유효한 토큰이면 Bearer access token을 발급한다")
        void issuesAccessToken() {
            given(refreshTokenRepository.findByTokenHash(tokenHash))
                    .willReturn(Optional.of(issuedToken(LocalDateTime.now().plusDays(1))));
            given(jwtProvider.createAccessToken(user)).willReturn(ACCESS_TOKEN);
            given(jwtProvider.getAccessExpirationSeconds()).willReturn(ACCESS_EXPIRATION_SECONDS);

            TokenResponse response = authService.reissue(RAW_TOKEN);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.accessTokenExpiresIn())
                    .as("JwtProvider가 유일한 ms→초 변환 지점이다 — 여기서 다시 나누면 만료가 1000배 짧아진다")
                    .isEqualTo(ACCESS_EXPIRATION_SECONDS);
        }

        @Test
        @DisplayName("원문이 아니라 SHA-256 hex 64자로 조회한다")
        void looksUpByHashNotRawValue() {
            given(refreshTokenRepository.findByTokenHash(anyString()))
                    .willReturn(Optional.of(issuedToken(LocalDateTime.now().plusDays(1))));
            given(jwtProvider.createAccessToken(user)).willReturn(ACCESS_TOKEN);
            given(jwtProvider.getAccessExpirationSeconds()).willReturn(ACCESS_EXPIRATION_SECONDS);

            authService.reissue(RAW_TOKEN);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            then(refreshTokenRepository).should().findByTokenHash(captor.capture());
            assertThat(captor.getValue())
                    .isNotEqualTo(RAW_TOKEN)
                    .hasSize(64)
                    .isEqualTo(tokenHash);
        }

        @Test
        @DisplayName("rotation을 하지 않으므로 기존 row를 건드리지 않는다")
        void doesNotRotateExistingRow() {
            given(refreshTokenRepository.findByTokenHash(tokenHash))
                    .willReturn(Optional.of(issuedToken(LocalDateTime.now().plusDays(1))));
            given(jwtProvider.createAccessToken(user)).willReturn(ACCESS_TOKEN);
            given(jwtProvider.getAccessExpirationSeconds()).willReturn(ACCESS_EXPIRATION_SECONDS);

            authService.reissue(RAW_TOKEN);

            then(refreshTokenRepository).should(never()).save(any());
            then(refreshTokenRepository).should(never()).deleteByTokenHash(anyString());
        }

        @Test
        @DisplayName("발급 이력이 없으면 A007")
        void rejectsUnknownToken() {
            given(refreshTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.reissue(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        @DisplayName("row가 남아 있어도 만료됐으면 A007 — 미존재와 같은 코드로 뭉갠다")
        void rejectsExpiredTokenWithSameCode() {
            given(refreshTokenRepository.findByTokenHash(tokenHash))
                    .willReturn(Optional.of(issuedToken(LocalDateTime.now().minusSeconds(1))));

            assertThatThrownBy(() -> authService.reissue(RAW_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);

            // 만료 row 정리는 후속 과제다 — expiresAt 검사가 이미 거절하므로 여기서 지우지 않는다
            then(refreshTokenRepository).should(never()).deleteByTokenHash(anyString());
        }

        @Test
        @DisplayName("쿠키가 없으면 조회조차 하지 않고 A007")
        void rejectsMissingCookieWithoutQuery() {
            assertThatThrownBy(() -> authService.reissue(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);

            assertThatThrownBy(() -> authService.reissue("   "))
                    .isInstanceOf(BusinessException.class);

            then(refreshTokenRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("logout — 멱등하다")
    class Logout {

        @Test
        @DisplayName("해당 기기의 토큰 1건만 해시로 삭제한다")
        void deletesSingleTokenByHash() {
            given(refreshTokenRepository.deleteByTokenHash(tokenHash)).willReturn(1);

            authService.logout(RAW_TOKEN);

            then(refreshTokenRepository).should().deleteByTokenHash(tokenHash);
            then(refreshTokenRepository).should(never()).deleteAllByUserId(anyLong());
        }

        @Test
        @DisplayName("이미 삭제된 토큰이어도 예외 없이 성공한다")
        void succeedsWhenNothingWasDeleted() {
            given(refreshTokenRepository.deleteByTokenHash(tokenHash)).willReturn(0);

            assertThatCode(() -> authService.logout(RAW_TOKEN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("쿠키가 없으면 삭제를 시도하지 않고 그대로 성공한다")
        void succeedsWithoutCookie() {
            assertThatCode(() -> authService.logout(null)).doesNotThrowAnyException();
            assertThatCode(() -> authService.logout("   ")).doesNotThrowAnyException();

            then(refreshTokenRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("탈퇴 보조")
    class WithdrawalSupport {

        @Test
        @DisplayName("revokeAllTokens는 해당 회원의 토큰을 전수 삭제한다")
        void revokesEveryToken() {
            given(refreshTokenRepository.deleteAllByUserId(USER_ID)).willReturn(3);

            authService.revokeAllTokens(USER_ID);

            then(refreshTokenRepository).should().deleteAllByUserId(USER_ID);
        }

        @Test
        @DisplayName("revokeAllTokens 실패는 그대로 전파된다 — fail-closed")
        void propagatesRevokeFailure() {
            willThrow(new RuntimeException("DB 장애"))
                    .given(refreshTokenRepository).deleteAllByUserId(USER_ID);

            assertThatThrownBy(() -> authService.revokeAllTokens(USER_ID))
                    .as("여기서 삼키면 회원은 WITHDRAWN인데 토큰이 살아 있는 상태가 된다")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 장애");
        }

        @Test
        @DisplayName("unlinkKakao는 카카오를 호출한다")
        void callsKakaoUnlink() {
            authService.unlinkKakao(PROVIDER_ID);

            then(kakaoOAuthClient).should().unlink(PROVIDER_ID);
        }

        @Test
        @DisplayName("카카오 장애로 unlink가 실패해도 예외가 새어나오지 않는다 — best-effort")
        void swallowsKakaoUnlinkFailure() {
            willThrow(new BusinessException(AuthErrorCode.KAKAO_API_ERROR))
                    .given(kakaoOAuthClient).unlink(PROVIDER_ID);

            assertThatCode(() -> authService.unlinkKakao(PROVIDER_ID))
                    .as("카카오 장애로 탈퇴가 막히면 안 된다")
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("설정·부품 위임")
    class Delegation {

        @Test
        @DisplayName("generateOAuthState는 SecureTokenGenerator를 재사용해 매번 다른 43자를 만든다")
        void generatesUniqueState() {
            String first = authService.generateOAuthState();
            String second = authService.generateOAuthState();

            assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("getRefreshTokenMaxAge는 설정값(ms)을 Duration으로 돌려준다")
        void exposesRefreshTokenMaxAge() {
            assertThat(authService.getRefreshTokenMaxAge())
                    .isEqualTo(Duration.ofMillis(REFRESH_EXPIRATION_MILLIS))
                    .isEqualTo(Duration.ofDays(7));
        }
    }
}
