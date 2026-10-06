package com.toyproject.backend.domain.cart.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CT001", "장바구니 항목을 찾을 수 없습니다."),
    CART_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "CT002", "재고를 초과한 수량입니다."),
    CART_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "CT003", "장바구니에 담을 수 있는 상품 종류를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
