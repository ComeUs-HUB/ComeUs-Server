package com.toyproject.backend.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * [1단계 · D] {@link ApiResponse} 불변식 검증
 *
 * <p>record의 canonical constructor는 record보다 접근성을 낮출 수 없어 <b>public으로 열려 있다</b>.
 * 정적 팩터리를 우회해 아무 조합이나 만들 수 있다는 뜻이므로, compact constructor가 무엇을 거부하는지를
 * 여기서 못박는다.
 *
 * <p>기존 테스트는 <b>정상 경로만</b> 지나간다. 즉 검증 블록을 통째로 지워도 전부 통과한다 —
 * 이 클래스가 없으면 그 검증을 지키는 것이 아무것도 없다.
 *
 * <p>⚠️ 가장 중요한 것은 {@code (true, null, null)}이 <b>통과</b>하는지다.
 * 데이터 없는 성공({@code POST /api/auth/logout}, {@code DELETE /api/users/me})이 여기 해당하고,
 * 불변식을 "정확히 하나만 non-null"로 조이는 순간 <b>두 엔드포인트가 500으로 깨진다.</b>
 */
@DisplayName("ApiResponse")
class ApiResponseTest {

    private static final ErrorResponse ERROR = new ErrorResponse("A007", "유효하지 않거나 만료된 리프레시 토큰입니다.", null);
    private static final String DATA = "payload";

    @Nested
    @DisplayName("유효한 조합은 그대로 만들어진다")
    class ValidCombinations {

        @Test
        @DisplayName("성공 + data — 일반 조회 응답")
        void successWithData() {
            ApiResponse<String> response = new ApiResponse<>(true, DATA, null);

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo(DATA);
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("성공 + data 없음 — 로그아웃·탈퇴가 이 형태다 (막으면 두 API가 깨진다)")
        void successWithoutData() {
            ApiResponse<Void> response = new ApiResponse<>(true, null, null);

            assertThat(response.success()).isTrue();
            assertThat(response.data())
                    .as("204를 쓰지 않으므로 data가 null인 채로 바디가 나간다")
                    .isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("실패 + error — 일반 오류 응답")
        void failureWithError() {
            ApiResponse<Void> response = new ApiResponse<>(false, null, ERROR);

            assertThat(response.success()).isFalse();
            assertThat(response.data()).isNull();
            assertThat(response.error()).isEqualTo(ERROR);
        }
    }

    @Nested
    @DisplayName("data와 error는 배타적이다")
    class Exclusivity {

        @Test
        @DisplayName("success=true인데 둘 다 값이 있으면 거부")
        void rejectsBothOnSuccess() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ApiResponse<>(true, DATA, ERROR));
        }

        @Test
        @DisplayName("success=false인데 둘 다 값이 있으면 거부")
        void rejectsBothOnFailure() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ApiResponse<>(false, DATA, ERROR));
        }
    }

    @Nested
    @DisplayName("success는 error의 부재와 일치해야 한다")
    class SuccessConsistency {

        @Test
        @DisplayName("성공인데 error가 있으면 거부")
        void rejectsErrorOnSuccess() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ApiResponse<>(true, null, ERROR));
        }

        @Test
        @DisplayName("실패인데 error가 없으면 거부 — 클라이언트가 error.code를 읽을 수 없다")
        void rejectsMissingErrorOnFailure() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ApiResponse<>(false, null, null));
        }

        @Test
        @DisplayName("실패인데 data가 있으면 거부")
        void rejectsDataOnFailure() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ApiResponse<>(false, DATA, null));
        }
    }

    @Nested
    @DisplayName("정적 팩터리")
    class Factories {

        @Test
        @DisplayName("success(data)는 error를 비운다")
        void successFactoryLeavesErrorNull() {
            ApiResponse<String> response = ApiResponse.success(DATA);

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo(DATA);
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("success(null)로 데이터 없는 성공을 만든다 — 인자 없는 success()는 접근자와 충돌해 만들 수 없다")
        void successFactoryAcceptsNull() {
            ApiResponse<Void> response = ApiResponse.success(null);

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isNull();
            assertThat(response.error()).isNull();
        }

        @Test
        @DisplayName("error(error)는 data를 비운다")
        void errorFactoryLeavesDataNull() {
            ApiResponse<Void> response = ApiResponse.error(ERROR);

            assertThat(response.success()).isFalse();
            assertThat(response.data()).isNull();
            assertThat(response.error()).isEqualTo(ERROR);
        }

        @Test
        @DisplayName("error(null)은 거부된다 — 팩터리만 써도 도달할 수 있는 유일한 무효 상태다")
        void errorFactoryRejectsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ApiResponse.error(null));
        }
    }
}
