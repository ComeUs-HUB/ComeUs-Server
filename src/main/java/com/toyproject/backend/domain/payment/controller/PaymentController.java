package com.toyproject.backend.domain.payment.controller;

import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
import com.toyproject.backend.domain.payment.service.PaymentQueryService;
import com.toyproject.backend.domain.payment.service.PaymentService;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Phase 4 · 10~11단계] 회원 결제 API. 계약은 {@code docs/domains/purchase/payment.md}.
 *
 * <p>같은 {@code paymentKey}의 재요청은 기존 결제를 돌려주므로 201이 아니라 200이다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService paymentQueryService;

    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.success(paymentService.confirm(principal.getId(), request));
    }

    /** [11단계] 내 주문의 결제 조회. 승인에서 PAY003을 받은 FE가 결과를 확인할 때 쓴다. */
    @GetMapping("/{orderNumber}")
    public ApiResponse<PaymentResponse> getPayment(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String orderNumber) {
        return ApiResponse.success(paymentQueryService.getPayment(principal.getId(), orderNumber));
    }
}
