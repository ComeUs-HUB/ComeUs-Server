package com.toyproject.backend.domain.order.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "주문을 찾을 수 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "O002", "현재 상태에서는 주문을 취소할 수 없습니다."),
    ORDER_PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "O003", "결제 승인 중인 주문은 취소할 수 없습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "O004", "이미 취소된 주문입니다."),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT, "O005", "허용되지 않는 주문 상태 변경입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
