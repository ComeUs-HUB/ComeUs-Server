package com.toyproject.backend.domain.payment.gateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * [Phase 4 · 9단계] 테스트용 {@link PaymentGateway}. 호출마다 돌려줄 결과나 던질 예외를 미리 정한다.
 *
 * <p>10단계부터 서비스 테스트가 쓴다. 호출 기록으로 "토스를 부르지 않았다"를 검증할 수 있다.
 * <p>스프링 빈으로 쓰면 테스트끼리 공유되므로 각 테스트 전에 {@link #reset()}을 부른다. 동시 호출 테스트를 위해 기록은 스레드 안전하다.
 */
public class FakePaymentGateway implements PaymentGateway {

    private volatile Supplier<TossPayment> onConfirm;
    private volatile Supplier<TossPayment> onLookup;
    private volatile Supplier<TossPayment> onCancel;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    public FakePaymentGateway() {
        reset();
    }

    /** 응답 설정과 호출 기록을 지운다. */
    public FakePaymentGateway reset() {
        onConfirm = () -> { throw new IllegalStateException("confirm 응답 미설정"); };
        onLookup = () -> { throw new IllegalStateException("getPayment 응답 미설정"); };
        onCancel = () -> { throw new IllegalStateException("cancel 응답 미설정"); };
        calls.clear();
        return this;
    }

    public FakePaymentGateway onConfirm(Supplier<TossPayment> response) {
        this.onConfirm = response;
        return this;
    }

    public FakePaymentGateway onLookup(Supplier<TossPayment> response) {
        this.onLookup = response;
        return this;
    }

    public FakePaymentGateway onCancel(Supplier<TossPayment> response) {
        this.onCancel = response;
        return this;
    }

    /** {@code "confirm:{paymentKey}"} 형식의 호출 기록. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public TossPayment confirm(String paymentKey, String orderId, BigDecimal amount) {
        calls.add("confirm:" + paymentKey);
        return onConfirm.get();
    }

    @Override
    public TossPayment getPayment(String paymentKey) {
        calls.add("getPayment:" + paymentKey);
        return onLookup.get();
    }

    @Override
    public TossPayment cancel(String paymentKey, String cancelReason) {
        calls.add("cancel:" + paymentKey);
        return onCancel.get();
    }
}
