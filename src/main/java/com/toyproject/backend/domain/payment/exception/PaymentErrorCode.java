package com.toyproject.backend.domain.payment.exception;

import com.toyproject.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAY002", "이미 결제가 완료된 주문입니다."),
    /** [Phase 4 · 10단계] 토스에서 승인됐을 수 있어 되돌리지 않는다. 복구 배치가 판정한다. */
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "PAY003", "결제 승인이 진행 중입니다. 잠시 후 주문 상태를 확인해 주세요."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAY004", "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_NOT_ALLOWED(HttpStatus.CONFLICT, "PAY005", "결제할 수 없는 주문 상태입니다."),
    PAYMENT_REJECTED(HttpStatus.PAYMENT_REQUIRED, "PAY006", "결제가 거절되었습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
