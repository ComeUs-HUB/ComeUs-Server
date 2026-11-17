package com.toyproject.backend.domain.payment.controller;

import com.toyproject.backend.domain.payment.dto.PaymentConfirmRequest;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
import com.toyproject.backend.domain.payment.service.PaymentService;
import com.toyproject.backend.global.response.ApiResponse;
import com.toyproject.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Phase 4 · 10단계] 회원 결제 API. 계약은 {@code docs/domains/purchase/payment.md}.
 *
 * <p>같은 {@code paymentKey}의 재요청은 기존 결제를 돌려주므로 201이 아니라 200이다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.success(paymentService.confirm(principal.getId(), request));
    }
}
