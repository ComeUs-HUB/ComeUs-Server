package com.toyproject.backend.global.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * [Phase 1] 예외를 공통 {@code ApiResponse} 포맷으로 바꾸는 단일 지점.
 *
 * <p>처리 대상 7종과 에러 코드 대응은 {@code docs/conventions/api-response.md}.
 * <p>필터 단계의 401·403은 여기 오지 않는다 — {@code JwtAuthenticationEntryPoint}가 맡는다.
 */
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
     * 경로 변수·쿼리 파라미터의 타입 불일치. {@code PUT /api/categories/abc} 같은 경우다.
     *
     * <p>없으면 fallback으로 떨어져 C003(500)이 된다 — 입력 오류가 서버 장애로 보고된다.
     * <p>{@code e.getMessage()}를 쓰지 않는다. 내부 타입명이 응답에 노출된다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: name={}, value={}", e.getName(), safeToString(e.getValue()));
        List<ErrorResponse.FieldError> errors = List.of(
                new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다.", safeToString(e.getValue())));
        return respond(CommonErrorCode.INVALID_INPUT_VALUE, errors);
    }

    /**
     * 요청 본문을 객체로 바꾸지 못할 때 — 깨진 JSON, {@code Integer} 범위 초과, 숫자 자리의 문자열.
     * {@code @Valid}보다 앞 단계라 {@link #handleValidationException}이 잡지 못한다.
     * 필드를 알면 {@code errors}에 담는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMostSpecificCause().getClass().getSimpleName());
        String field = unreadableField(e);
        List<ErrorResponse.FieldError> errors = (field == null)
                ? null
                : List.of(new ErrorResponse.FieldError(field, "형식이 올바르지 않습니다.", null));
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

    /** 본문의 Content-Type을 받지 않을 때(없음·{@code text/plain} 등). 없으면 fallback으로 떨어져 500이 된다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: contentType={}", e.getContentType());
        return respond(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
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

    /** Jackson이 경로를 남긴 경우의 필드명. 깨진 JSON처럼 필드 이전에 실패하면 {@code null}이다. */
    private String unreadableField(HttpMessageNotReadableException e) {
        if (!(e.getCause() instanceof JsonMappingException mapping) || mapping.getPath().isEmpty()) {
            return null;
        }
        return mapping.getPath().get(mapping.getPath().size() - 1).getFieldName();
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        return str.length() > REJECTED_VALUE_MAX_LENGTH ? str.substring(0, REJECTED_VALUE_MAX_LENGTH) + "..." : str;
    }
}
