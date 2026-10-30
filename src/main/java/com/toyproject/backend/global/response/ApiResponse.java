package com.toyproject.backend.global.response;

/**
 * [Phase 1] 모든 API 응답을 감싸는 공통 포맷. {@code success}는 {@code error}의 부재와 일치한다.
 *
 * <p>불변식은 compact 생성자가 강제한다. {@code (true, null, null)}은 데이터 없는 성공으로
 * 유효하다 — "정확히 하나만 non-null"로 조이면 로그아웃·탈퇴가 깨진다.
 * <p>스키마·불변식 표·컨트롤러 반환 타입은 {@code docs/conventions/api-response.md}.
 *
 * @param <T> 성공 본문 타입. 데이터 없는 성공은 {@code Void}
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /** 배타성은 별도 검사 없이 성립한다 — 성공은 첫 검사가, 실패는 셋째 검사가 막는다. */
    public ApiResponse {
        if (success && error != null) {
            throw new IllegalArgumentException("success 응답에는 error가 있을 수 없습니다.");
        }
        if (!success && error == null) {
            throw new IllegalArgumentException("실패 응답에는 error가 반드시 있어야 합니다.");
        }
        if (!success && data != null) {
            throw new IllegalArgumentException("실패 응답에는 data가 있을 수 없습니다.");
        }
    }

    /** 성공 응답. {@code data}가 {@code null}이면 데이터 없는 성공이다 — 204를 쓰지 않는다. */
    public static <T> ApiResponse<T> success(T data) {

        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답. 반환 타입이 {@code Void}인 것은 실패에 {@code data}가 없기 때문이다.
     *
     * @throws IllegalArgumentException {@code error}가 {@code null}일 때
     */
    public static ApiResponse<Void> error(ErrorResponse error) {

        return new ApiResponse<>(false, null, error);
    }
}
