package com.toyproject.backend.domain.payment.gateway.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.PaymentGateway;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * [Phase 4 · 9단계] 토스페이먼츠 구현. 모든 실패를 확정 거절 또는 결과 불명으로 바꾸는 것이 핵심 책임이다.
 *
 * <p>목록에 있는 코드만 거절이고, 5xx·타임아웃·해석 불가는 결과 불명이다(ADR 0028·0029).
 * <p>멱등키 규칙도 여기서만 안다 — 승인 {@code {paymentKey}}, 취소 {@code cancel-{paymentKey}}.
 */
@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient paymentClient;
    private final RestClient lookupClient;
    private final ObjectMapper objectMapper;

    public TossPaymentGateway(@Qualifier("tossRestClient") RestClient paymentClient,
                              @Qualifier("tossLookupRestClient") RestClient lookupClient,
                              ObjectMapper objectMapper) {
        this.paymentClient = paymentClient;
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
    }

    /** 토스 승인 API를 부른다. 멱등키는 {@code paymentKey}다. */
    @Override
    public TossPayment confirm(String paymentKey, String orderId, BigDecimal amount) {
        Operation op = Operation.CONFIRM;
        return execute(op, () -> paymentClient.post()
                .uri("/v1/payments/confirm")
                .header(IDEMPOTENCY_KEY, paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmRequest(paymentKey, orderId, toWon(amount)))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw classify(op, response);
                })
                .body(TossPayment.class));
    }

    /** 토스 결제를 조회한다. 복구 배치용이라 읽기 타임아웃이 짧은 클라이언트를 쓴다. */
    @Override
    public TossPayment getPayment(String paymentKey) {
        Operation op = Operation.LOOKUP;
        return execute(op, () -> lookupClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw classify(op, response);
                })
                .body(TossPayment.class));
    }

    /** 토스 결제를 전액 취소한다. 멱등키는 {@code cancel-{paymentKey}}다. */
    @Override
    public TossPayment cancel(String paymentKey, String cancelReason) {
        Operation op = Operation.CANCEL;
        return execute(op, () -> paymentClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(IDEMPOTENCY_KEY, "cancel-" + paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelRequest(cancelReason))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw classify(op, response);
                })
                .body(TossPayment.class));
    }

    /** 요청을 실행하고, 응답을 못 받은 실패(타임아웃·연결·역직렬화)를 결과 불명으로 바꾼다. */
    private TossPayment execute(Operation op, Supplier<TossPayment> call) {
        try {
            TossPayment result = call.get();
            if (result == null) {
                throw new PaymentResultUnknownException(null, false, op + " 응답 body 없음", null);
            }
            return result;
        } catch (RestClientException e) {
            throw new PaymentResultUnknownException(null, false, op + " 통신 실패", e);
        }
    }

    /** 에러 응답을 호출별 목록과 대조해 확정 거절 또는 결과 불명 예외를 만든다. */
    private RuntimeException classify(Operation op, ClientHttpResponse response) throws IOException {
        TossError error = readError(response);
        // 5xx는 코드와 무관하게 결과 불명이다. 서버 쪽에서 처리됐을 수 있다
        boolean serverError = response.getStatusCode().is5xxServerError();
        if (!serverError && error.code() != null && op.rejected.contains(error.code())) {
            return new PaymentRejectedException(error.code(), error.message());
        }
        boolean alert = error.code() != null && op.alert.contains(error.code());
        return new PaymentResultUnknownException(
                error.code(), alert, op + " status=" + response.getStatusCode().value() + " " + error.message(), null);
    }

    /** 에러 본문에서 code·message를 꺼낸다. 해석할 수 없으면 코드 없음이라 결과 불명이 된다. */
    private TossError readError(ClientHttpResponse response) {
        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            return new TossError(text(body, "code"), text(body, "message"));
        } catch (IOException | RuntimeException e) {
            return new TossError(null, "에러 응답을 해석할 수 없음");
        }
    }

    /** JSON 필드를 문자열로 읽는다. 없거나 null이면 null. */
    private static String text(JsonNode body, String field) {
        JsonNode node = body == null ? null : body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 금액을 토스가 받는 정수 원 단위로 바꾼다. 소수가 있으면 보내지 않고 실패시킨다. */
    private static long toWon(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private enum Operation {
        CONFIRM(TossErrorCodes.CONFIRM_REJECTED, TossErrorCodes.CONFIRM_ALERT),
        LOOKUP(TossErrorCodes.LOOKUP_REJECTED, Set.of()),
        CANCEL(TossErrorCodes.CANCEL_REJECTED, TossErrorCodes.CANCEL_ALERT);

        private final Set<String> rejected;
        private final Set<String> alert;

        Operation(Set<String> rejected, Set<String> alert) {
            this.rejected = rejected;
            this.alert = alert;
        }
    }

    private record ConfirmRequest(String paymentKey, String orderId, long amount) {
    }

    private record CancelRequest(String cancelReason) {
    }

    private record TossError(String code, String message) {
    }
}
