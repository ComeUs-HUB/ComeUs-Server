package com.toyproject.backend.global.exception;

import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final int REJECTED_VALUE_MAX_LENGTH = 200;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), errorCode.getMessage());
        return respond(errorCode, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage(), safeToString(fe.getRejectedValue())))
                .toList();
        return respond(CommonErrorCode.INVALID_INPUT_VALUE, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
                .map(cv -> new ErrorResponse.FieldError(cv.getPropertyPath().toString(), cv.getMessage(), safeToString(cv.getInvalidValue())))
                .toList();
        return respond(CommonErrorCode.INVALID_INPUT_VALUE, errors);
    }

    /**
     * 경로 변수·쿼리 파라미터의 타입이 맞지 않을 때. {@code PUT /api/categories/abc}처럼
     * {@code Long} 자리에 문자열이 온 경우다.
     *
     * <p><b>추가 이유</b>: 이 핸들러가 없으면 {@code Exception} fallback으로 떨어져
     * <b>C003(500)</b>이 나간다. 클라이언트 입력 오류가 서버 장애로 보고되고 {@code log.error}까지
     * 남는다. Phase 3에서 {@code /api/categories/{id}}가 <b>이 프로젝트 최초의 경로 변수</b>로
     * 들어오면서 실제 도달 경로가 생겼다 — 그전까지는 {@code /api/users/me}처럼 경로 변수가 없어
     * 드러나지 않았다.
     *
     * <p>⚠️ {@code e.getMessage()}를 그대로 쓰지 않는다. {@code "Failed to convert value of type
     * 'java.lang.String' to required type 'java.lang.Long'"}처럼 <b>내부 타입명이 응답에 노출</b>된다.
     * 필요한 정보는 "어느 파라미터의 어떤 값이 거절됐는가"뿐이고, 그건 {@code errors}가 담는다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: name={}, value={}", e.getName(), safeToString(e.getValue()));
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다.", safeToString(e.getValue())));
        return respond(CommonErrorCode.INVALID_INPUT_VALUE, errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolationException: {}", e.getMessage());
        return respond(CommonErrorCode.RESOURCE_CONFLICT, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        return respond(CommonErrorCode.RESOURCE_NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return respond(CommonErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, List<ErrorResponse.FieldError> errors) {
        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(error));
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        return str.length() > REJECTED_VALUE_MAX_LENGTH ? str.substring(0, REJECTED_VALUE_MAX_LENGTH) + "..." : str;
    }
}
