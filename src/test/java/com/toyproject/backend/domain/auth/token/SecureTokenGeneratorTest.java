package com.toyproject.backend.domain.auth.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [5단계 · I] {@link SecureTokenGenerator} 검증
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는 순수 단위 테스트</b>다. 의존성이 없어 그냥 {@code new} 하면 된다.
 *
 * <p>이 단계의 완료 조건 중 <b>"해시 64자"</b>가 여기에 해당한다. 길이는 단순한 숫자가 아니라
 * {@code refresh_tokens.token_hash}의 {@code CHAR(64)}와 맺은 계약이라, 어긋나면 7단계에서
 * 저장 시점에 잘리거나 실패한다.
 */
@DisplayName("SecureTokenGenerator")
class SecureTokenGeneratorTest {

    private final SecureTokenGenerator generator = new SecureTokenGenerator();

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("32바이트 랜덤을 base64url로 인코딩해 43자를 반환한다")
        void length() {
            assertThat(generator.generate()).hasSize(43);
        }

        @Test
        @DisplayName("base64url 문자만 쓴다 — 쿠키·URL에 그대로 실을 수 있어야 한다")
        void urlSafeCharsetOnly() {
            // 일반 base64의 '+', '/', '='가 섞이면 쿠키 파싱·URL 전달에서 깨진다.
            assertThat(generator.generate()).matches("^[A-Za-z0-9_-]{43}$");
        }

        @Test
        @DisplayName("호출할 때마다 다른 값을 반환한다 — 예측 가능하면 세션을 탈취당한다")
        void unique() {
            Set<String> tokens = new HashSet<>();
            IntStream.range(0, 1_000).forEach(i -> tokens.add(generator.generate()));

            assertThat(tokens).hasSize(1_000);
        }
    }

    @Nested
    @DisplayName("sha256Hex")
    class Sha256Hex {

        @Test
        @DisplayName("소문자 hex 64자를 반환한다 — token_hash CHAR(64)와 정확히 맞는다")
        void lowercaseHex64() {
            String hash = generator.sha256Hex(generator.generate());

            assertThat(hash).hasSize(64);
            assertThat(hash).matches("^[0-9a-f]{64}$");
        }

        @Test
        @DisplayName("같은 원문은 항상 같은 해시가 된다 — 해시로 조회하므로 결정적이어야 한다")
        void deterministic() {
            String raw = generator.generate();

            assertThat(generator.sha256Hex(raw)).isEqualTo(generator.sha256Hex(raw));
        }

        @Test
        @DisplayName("다른 원문은 다른 해시가 된다")
        void distinct() {
            assertThat(generator.sha256Hex("token-a")).isNotEqualTo(generator.sha256Hex("token-b"));
        }

        @Test
        @DisplayName("알려진 SHA-256 벡터와 일치한다 — 알고리즘·인코딩 오지정을 잡는다")
        void knownVector() {
            // 빈 문자열의 SHA-256. 알고리즘을 잘못 넣거나(MD5 등) hex 변환이 틀리면 여기서 걸린다.
            assertThat(generator.sha256Hex(""))
                    .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

            // UTF-8 바이트로 해시하는지 확인 (플랫폼 기본 인코딩에 의존하면 환경마다 값이 달라진다).
            assertThat(generator.sha256Hex("abc"))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        }
    }
}
