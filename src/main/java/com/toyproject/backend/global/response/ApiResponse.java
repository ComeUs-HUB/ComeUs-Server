package com.toyproject.backend.global.response;

public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /*성공 응답*/
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /*실패 응답*/
    public static ApiResponse<Void> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
