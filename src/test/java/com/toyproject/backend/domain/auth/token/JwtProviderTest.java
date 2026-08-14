package com.toyproject.backend.domain.auth.token;

import com.toyproject.backend.domain.auth.config.JwtProperties;
import com.toyproject.backend.domain.user.entity.Role;
import com.toyproject.backend.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * [5단계 · I] {@link JwtProvider} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 생성자 주입 컴포넌트라 직접 조립할 수 있고,
 * {@code JwtProperties}가 record라 그냥 {@code new} 하면 된다. 만료 동작은 설정값을 바꾼
 * provider를 따로 만들어 재현하므로 테스트에서 시간을 기다릴 필요가 없다.
 *
 * <p>이 단계의 완료 조건은 <b>생성·파싱 왕복과 만료/위조 예외 구분</b>이다.
 * 특히 예외 구분은 6단계에서 A004(만료)와 A003(위조)을 가르는 <b>유일한 근거</b>라,
 * 타입을 뭉뚱그리지 않고 정확히 갈라서 단언한다.
 */
@DisplayName("JwtProvider")
class JwtProviderTest {

    /** HS256이라 32바이트 이상이어야 한다. */
    private static final String SECRET = "unit-test-jwt-secret-key-32bytes-or-longer!";
    private static final String OTHER_SECRET = "another-jwt-secret-key-32bytes-or-longer!!";

    private static final long ACCESS_EXPIRATION_MS = 1_800_000L;  // 30분
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7일

    private static final long USER_ID = 42L;

    private final JwtProvider jwtProvider = providerWith(SECRET, ACCESS_EXPIRATION_MS);

    private static JwtProvider providerWith(String secret, long accessExpirationMs) {
        return new JwtProvider(new JwtProperties(secret, accessExpirationMs, REFRESH_EXPIRATION_MS));
    }

    /**
     * {@code User.ofKakao}는 아직 영속화 전이라 {@code id}가 {@code null}이다.
     * {@code createAccessToken}이 subject에 id를 넣으므로 여기서 채워 준다
     * (DB를 띄우지 않고 엔티티 상태만 필요한 상황이다).
     */
    private static User userWithId(long id, Role role) {
        User user = User.ofKakao("kakao-" + id, "user" + id + "@kakao.com", "민준");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Nested
    @DisplayName("createAccessToken → parseClaims 왕복")
    class RoundTrip {

        @Test
        @DisplayName("subject에서 userId를 되찾는다 — CustomUserDetailsService가 이 값으로 조회한다")
        void subjectIsUserId() {
            String token = jwtProvider.createAccessToken(userWithId(USER_ID, Role.USER));

            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.getSubject()).isEqualTo(String.valueOf(USER_ID));
        }

        @Test
        @DisplayName("role claim이 보존된다")
        void roleClaim() {
            String token = jwtProvider.createAccessToken(userWithId(USER_ID, Role.ADMIN));

            Claims claims = jwtProvider.parseClaims(token);

            assertThat(claims.get(JwtProvider.CLAIM_ROLE, String.class)).isEqualTo(Role.ADMIN.name());
        }

        @Test
        @DisplayName("만료 시각이 발급 시각 + access-expiration이다")
        void expirationMatchesConfiguration() {
            String token = jwtProvider.createAccessToken(userWithId(USER_ID, Role.USER));

            Claims claims = jwtProvider.parseClaims(token);
            long actualMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

            // JWT의 exp/iat는 초 단위라 최대 1초가 절삭될 수 있다.
            assertThat(actualMs).isBetween(ACCESS_EXPIRATION_MS - 1_000, ACCESS_EXPIRATION_MS);
        }
    }

    @Nested
    @DisplayName("parseClaims 실패 — 6단계의 A003/A004 분기 근거")
    class Failure {

        @Test
        @DisplayName("만료된 토큰은 ExpiredJwtException을 던진다 (→ A004)")
        void expired() {
            // 만료를 음수로 준 provider는 '이미 만료된' 토큰을 만든다. 테스트에서 30분을 기다릴 수 없다.
            String expiredToken = providerWith(SECRET, -1_000L)
                    .createAccessToken(userWithId(USER_ID, Role.USER));

            Throwable thrown = catchThrowable(() -> jwtProvider.parseClaims(expiredToken));

            assertThat(thrown).isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰은 JwtException을 던지되 ExpiredJwtException은 아니다 (→ A003)")
        void forgedSignature() {
            String forged = providerWith(OTHER_SECRET, ACCESS_EXPIRATION_MS)
                    .createAccessToken(userWithId(USER_ID, Role.USER));

            Throwable thrown = catchThrowable(() -> jwtProvider.parseClaims(forged));

            assertThat(thrown).isInstanceOf(JwtException.class);
            // ⚠️ 이 단언이 핵심이다. ExpiredJwtException은 JwtException의 하위 타입이라
            // 상위 타입만 확인하면 '만료를 위조로 뭉갠' 구현도 통과해 버린다.
            assertThat(thrown).isNotInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("JWT 형식이 아니면 JwtException을 던진다 (→ A003)")
        void malformed() {
            Throwable thrown = catchThrowable(() -> jwtProvider.parseClaims("not-a-jwt"));

            assertThat(thrown).isInstanceOf(JwtException.class);
            assertThat(thrown).isNotInstanceOf(ExpiredJwtException.class);
        }
    }

    @Test
    @DisplayName("getAccessExpirationSeconds — ms 설정값을 초로 바꿔 응답에 싣는다")
    void accessExpirationSeconds() {
        assertThat(jwtProvider.getAccessExpirationSeconds()).isEqualTo(1_800L);
    }
}
