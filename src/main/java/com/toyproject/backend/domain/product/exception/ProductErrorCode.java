package com.toyproject.backend.domain.product.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "상품을 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "P002", "재고가 부족합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
