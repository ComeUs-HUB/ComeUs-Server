package com.toyproject.backend.domain.order.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "주문을 찾을 수 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "O002", "배송 중인 주문은 취소할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
