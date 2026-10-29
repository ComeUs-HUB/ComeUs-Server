package com.toyproject.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Phase 2 · 6단계] {@link AppProperties} 검증
 *
 * <p>누락 시 동작이 값마다 다르다(ADR 0022). 그 경계를 값으로 못박는다 —
 * 흡수해야 하는 값에서 기동이 막히거나, 막아야 하는 값이 조용히 통과하면 둘 다 결함이다.
 */
@DisplayName("AppProperties")
class AppPropertiesTest {

    private static final AppProperties.Frontend FRONTEND =
            new AppProperties.Frontend("http://localhost:3000");

    @Nested
    @DisplayName("base-url — fail-fast")
    class BaseUrl {

        @Test
        @DisplayName("app.frontend가 통째로 없으면 기동에서 거부한다")
        void rejectsMissingSection() {
            assertThatThrownBy(() -> new AppProperties(null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("app.frontend.base-url");
        }

        @Test
        @DisplayName("base-url이 null이면 거부한다 — 없으면 \"null/oauth/complete\"로 리다이렉트된다")
        void rejectsNull() {
            assertThatThrownBy(() -> new AppProperties(new AppProperties.Frontend(null), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("공백만 있어도 거부한다")
        void rejectsBlank() {
            assertThatThrownBy(() -> new AppProperties(new AppProperties.Frontend("  "), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("나머지 값 — fail-safe")
    class FailSafe {

        @Test
        @DisplayName("app.cors가 없으면 빈 목록이다 — 모든 cross-origin이 막힌다")
        void corsDefaultsToEmpty() {
            AppProperties properties = new AppProperties(FRONTEND, null, null);

            assertThat(properties.cors().allowedOrigins()).isEmpty();
        }

        @Test
        @DisplayName("app.cookie가 없으면 secure는 true다 — 꺼지는 쪽으로 실패하지 않는다")
        void secureDefaultsToEnabled() {
            AppProperties properties = new AppProperties(FRONTEND, null, null);

            assertThat(properties.cookie().secure()).isTrue();
        }

        @Test
        @DisplayName("allowed-origins는 복사해 담는다 — 밖에서 목록을 고쳐도 영향이 없다")
        void copiesAllowedOrigins() {
            List<String> origins = new java.util.ArrayList<>(List.of("http://a"));
            AppProperties properties = new AppProperties(FRONTEND, new AppProperties.Cors(origins), null);
            origins.add("http://b");

            assertThat(properties.cors().allowedOrigins()).containsExactly("http://a");
        }

        @Test
        @DisplayName("base-url만 있으면 나머지가 비어도 생성된다")
        void buildsWithOnlyBaseUrl() {
            assertThatCode(() -> new AppProperties(FRONTEND, null, null))
                    .doesNotThrowAnyException();
        }
    }
}
