package com.toyproject.backend.domain.payment.service;

import com.toyproject.backend.domain.order.entity.Order;
import com.toyproject.backend.domain.order.exception.OrderErrorCode;
import com.toyproject.backend.domain.order.repository.OrderRepository;
import com.toyproject.backend.domain.payment.dto.PaymentResponse;
import com.toyproject.backend.domain.payment.exception.PaymentErrorCode;
import com.toyproject.backend.domain.payment.repository.PaymentRepository;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Phase 4 · 11단계] 결제 조회. {@link PaymentService}는 트랜잭션을 두지 않는 것이 설계라 읽기를 따로 둔다.
 *
 * <p>FE는 승인에서 {@code PAY003}을 받은 뒤 이 API로 {@code PAID} 여부를 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 내 주문의 결제를 조회한다. 성공한 결제만 저장하므로 승인 전·거절·판정 전이면 {@code PAY001}이다.
     *
     * @throws BusinessException 없거나 타인의 주문이면 O001, 결제가 없으면 PAY001
     */
    public PaymentResponse getPayment(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return paymentRepository.findByOrderId(order.getId())
                .map(payment -> PaymentResponse.of(order.getOrderNumber(), payment))
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
