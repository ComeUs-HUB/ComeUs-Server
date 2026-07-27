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
