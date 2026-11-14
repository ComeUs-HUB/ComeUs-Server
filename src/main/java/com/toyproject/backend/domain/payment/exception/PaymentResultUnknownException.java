package com.toyproject.backend.domain.payment.exception;

import lombok.Getter;

/**
 * [Phase 4 · 9단계] 토스에서 처리됐는지 알 수 없는 실패. 호출자는 상태를 되돌리지 않는다.
 *
 * <p>목록에 없는 코드, 5xx, 타임아웃, 해석할 수 없는 응답이 모두 여기 온다(ADR 0028).
 * <p>{@code alert}는 우리 설정 오류나 쓰지 않는 기능의 코드라는 표시다. 로그 레벨은 호출 경로가 정한다(ADR 0030).
 */
@Getter
public class PaymentResultUnknownException extends RuntimeException {

    /** 토스 에러 코드. 응답을 받지 못했거나 해석하지 못했으면 null. */
    private final String code;
    private final boolean alert;

    public PaymentResultUnknownException(String code, boolean alert, String detail, Throwable cause) {
        super(code + ": " + detail, cause);
        this.code = code;
        this.alert = alert;
    }
}
