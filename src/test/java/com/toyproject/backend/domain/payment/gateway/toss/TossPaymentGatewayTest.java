package com.toyproject.backend.domain.payment.gateway.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.backend.domain.payment.exception.PaymentRejectedException;
import com.toyproject.backend.domain.payment.exception.PaymentResultUnknownException;
import com.toyproject.backend.domain.payment.gateway.TossPayment;
import com.toyproject.backend.domain.payment.gateway.TossPaymentStatus;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * [Phase 4 · 9단계] 토스 응답이 확정 거절과 결과 불명으로 맞게 나뉘는지 검증한다.
 *
 * <p>결과 불명이 거절로 새면 결제됐을 수 있는 주문을 되돌려 중복 청구가 난다(ADR 0028).
 */
@DisplayName("TossPaymentGateway")
class TossPaymentGatewayTest {

    private static final String BASE = "https://api.tosspayments.com";
    private static final String KEY = "tgen_20261111ABCD";
    private static final String ORDER_NUMBER = "ORD-20261111-A1B2C3D4E5F6";

    private MockRestServiceServer paymentServer;
    private MockRestServiceServer lookupServer;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder paymentBuilder = client();
        paymentServer = MockRestServiceServer.bindTo(paymentBuilder).build();
        RestClient.Builder lookupBuilder = client();
        lookupServer = MockRestServiceServer.bindTo(lookupBuilder).build();
        gateway = new TossPaymentGateway(paymentBuilder.build(), lookupBuilder.build(), new ObjectMapper());
    }

    // 실제 설정과 같은 기본 헤더를 쓴다. requestFactory는 목 서버가 대신한다
    private static RestClient.Builder client() {
        return RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, TossRestClientConfig.basicAuth("test_sk"));
    }

    private static String error(String code) {
        return "{\"code\":\"" + code + "\",\"message\":\"m\"}";
    }

    private static String payment(String status) {
        return """
                {"paymentKey":"%s","orderId":"%s","status":"%s","totalAmount":128000,"method":"카드",
                 "approvedAt":"2026-11-11T10:15:30+09:00","cancels":null,"mId":"tosspayments"}
                """.formatted(KEY, ORDER_NUMBER, status);
    }

    private void confirmFails(HttpStatus status, String body) {
        paymentServer.expect(requestTo(BASE + "/v1/payments/confirm"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private Throwable confirm() {
        return catchThrowable(() -> gateway.confirm(KEY, ORDER_NUMBER, new BigDecimal("128000.00")));
    }

    @Nested
    @DisplayName("승인")
    class Confirm {

        @Test
        @DisplayName("Basic 인증·멱등키·정수 금액으로 요청하고 응답을 금액·시각까지 해석한다")
        void requestAndResponse() {
            paymentServer.expect(requestTo(BASE + "/v1/payments/confirm"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dGVzdF9zazo="))   // "test_sk:" — 콜론 필수
                    .andExpect(header("Idempotency-Key", KEY))
                    .andExpect(content().json("{\"paymentKey\":\"" + KEY + "\",\"orderId\":\"" + ORDER_NUMBER
                            + "\",\"amount\":128000}", true))
                    .andRespond(withSuccess(payment("DONE"), MediaType.APPLICATION_JSON));

            TossPayment result = gateway.confirm(KEY, ORDER_NUMBER, new BigDecimal("128000.00"));

            assertThat(result.status()).isEqualTo(TossPaymentStatus.DONE);
            assertThat(result.matches(ORDER_NUMBER, new BigDecimal("128000.00")))
                    .as("128000 과 128000.00 은 equals로는 다르다 — compareTo로 비교해야 한다").isTrue();
            assertThat(result.approvedAtLocal().atZone(ZoneId.systemDefault()).toInstant())
                    .as("오프셋을 떼지 않고 같은 순간으로 옮긴다")
                    .isEqualTo(OffsetDateTime.parse("2026-11-11T10:15:30+09:00").toInstant());
        }

        @Test
        @DisplayName("거절 목록의 코드만 확정 거절이다")
        void rejectedCode() {
            confirmFails(HttpStatus.FORBIDDEN, error("REJECT_CARD_PAYMENT"));

            assertThat(confirm()).isInstanceOfSatisfying(PaymentRejectedException.class,
                    e -> assertThat(e.getCode()).isEqualTo("REJECT_CARD_PAYMENT"));
        }

        @Test
        @DisplayName("목록에 없는 4xx 코드는 결과 불명이다 — 400이어도 일시 오류일 수 있다")
        void unlistedCodeIsUnknown() {
            confirmFails(HttpStatus.BAD_REQUEST, error("PROVIDER_ERROR"));

            assertThat(confirm()).isInstanceOfSatisfying(PaymentResultUnknownException.class, e -> {
                assertThat(e.getCode()).isEqualTo("PROVIDER_ERROR");
                assertThat(e.isAlert()).isFalse();
            });
        }

        @Test
        @DisplayName("설정 오류 코드는 결과 불명이면서 경보 대상이다")
        void alertCode() {
            confirmFails(HttpStatus.UNAUTHORIZED, error("UNAUTHORIZED_KEY"));

            assertThat(confirm()).isInstanceOfSatisfying(PaymentResultUnknownException.class,
                    e -> assertThat(e.isAlert()).isTrue());
        }

        @Test
        @DisplayName("5xx는 거절 목록의 코드가 와도 결과 불명이다")
        void serverErrorIsUnknownEvenWithRejectedCode() {
            confirmFails(HttpStatus.INTERNAL_SERVER_ERROR, error("REJECT_CARD_PAYMENT"));

            assertThat(confirm()).isInstanceOf(PaymentResultUnknownException.class);
        }

        @Test
        @DisplayName("해석할 수 없는 에러 body는 코드 없는 결과 불명이다")
        void unparsableBodyIsUnknown() {
            paymentServer.expect(requestTo(BASE + "/v1/payments/confirm"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>"));

            assertThat(confirm()).isInstanceOfSatisfying(PaymentResultUnknownException.class,
                    e -> assertThat(e.getCode()).isNull());
        }

        @Test
        @DisplayName("타임아웃은 결과 불명이다 — 토스에서 승인됐을 수 있다")
        void timeoutIsUnknown() {
            paymentServer.expect(requestTo(BASE + "/v1/payments/confirm"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThat(confirm()).isInstanceOf(PaymentResultUnknownException.class);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("cancel-{paymentKey} 멱등키로 전액 취소를 요청한다")
        void request() {
            paymentServer.expect(requestTo(BASE + "/v1/payments/" + KEY + "/cancel"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "cancel-" + KEY))
                    .andExpect(content().json("{\"cancelReason\":\"고객 요청\"}", true))   // cancelAmount 없음 = 전액
                    .andRespond(withSuccess(payment("CANCELED"), MediaType.APPLICATION_JSON));

            assertThat(gateway.cancel(KEY, "고객 요청").status()).isEqualTo(TossPaymentStatus.CANCELED);
        }

        @Test
        @DisplayName("취소 거절 목록의 코드는 확정 거절이다")
        void rejectedCode() {
            paymentServer.expect(requestTo(BASE + "/v1/payments/" + KEY + "/cancel"))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                            .body(error("REFUND_REJECTED")));

            assertThat(catchThrowable(() -> gateway.cancel(KEY, "r"))).isInstanceOf(PaymentRejectedException.class);
        }
    }

    @Test
    @DisplayName("같은 NOT_FOUND_PAYMENT도 승인·조회에선 거절, 취소에선 경보 대상 결과 불명이다")
    void sameCodeDiffersByOperation() {
        confirmFails(HttpStatus.NOT_FOUND, error("NOT_FOUND_PAYMENT"));
        lookupServer.expect(requestTo(BASE + "/v1/payments/" + KEY))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(error("NOT_FOUND_PAYMENT")));
        paymentServer.expect(requestTo(BASE + "/v1/payments/" + KEY + "/cancel"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(error("NOT_FOUND_PAYMENT")));

        assertThat(confirm()).isInstanceOf(PaymentRejectedException.class);
        assertThat(catchThrowable(() -> gateway.getPayment(KEY))).isInstanceOf(PaymentRejectedException.class);
        assertThat(catchThrowable(() -> gateway.cancel(KEY, "r")))
                .as("결제를 저장해 둔 키로 취소하는데 없다면 설계 전제가 깨진 것이다 — ADR 0029")
                .isInstanceOfSatisfying(PaymentResultUnknownException.class, e -> assertThat(e.isAlert()).isTrue());
    }

    @Test
    @DisplayName("조회 응답의 모르는 상태는 실패가 아니라 UNKNOWN이다")
    void unknownStatusDoesNotFail() {
        lookupServer.expect(requestTo(BASE + "/v1/payments/" + KEY))
                .andRespond(withSuccess(payment("SOMETHING_NEW"), MediaType.APPLICATION_JSON));

        assertThat(gateway.getPayment(KEY).status()).isEqualTo(TossPaymentStatus.UNKNOWN);
    }
}
