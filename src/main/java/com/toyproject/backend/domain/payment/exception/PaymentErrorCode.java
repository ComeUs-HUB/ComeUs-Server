package com.toyproject.backend.domain.payment.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAY002", "이미 결제가 완료된 주문입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
