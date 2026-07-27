package com.toyproject.backend.domain.category.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CA001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "CA002", "하위 카테고리가 존재하여 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
