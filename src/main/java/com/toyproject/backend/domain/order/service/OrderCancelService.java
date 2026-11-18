package com.toyproject.backend.domain.order.service;

import com.toyproject.backend.domain.order.entity.OrderStatus;
import com.toyproject.backend.domain.payment.service.RefundService;
import com.toyproject.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [Phase 4 · 12단계] 주문 취소의 진입점. 상태를 보고 결제 전 취소와 환불로 가른다(ADR 0029).
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 환불은 토스 취소를 트랜잭션 밖에서 불러야 한다.
 * <p>여기서 읽은 상태는 판단용이다. 그사이 상태가 바뀌어도 각 경로의 CAS가 0건이 되고 지금 상태로 거절한다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    static final String MEMBER_CANCEL_REASON = "고객 요청";
    static final String ADMIN_CANCEL_REASON = "관리자 취소";

    private final OrderService orderService;
    private final RefundService refundService;

    /**
     * 회원이 자기 주문을 취소한다. {@code PAID}면 환불한다.
     *
     * @throws BusinessException O001 · O002 · O003 · O004 · PAY007 · PAY008
     */
    public void cancelByMember(Long userId, String orderNumber) {
        cancel(orderService.getMyOrderId(userId, orderNumber), MEMBER_CANCEL_REASON);
    }

    /**
     * ADMIN이 주문을 취소한다. 규칙은 회원 취소와 같다.
     *
     * @throws BusinessException O001 · O002 · O003 · O004 · PAY007 · PAY008
     */
    public void cancelByAdmin(Long orderId) {
        cancel(orderId, ADMIN_CANCEL_REASON);
    }

    private void cancel(Long orderId, String reason) {
        if (orderService.getOrderStatus(orderId) == OrderStatus.PAID) {
            refundService.refund(orderId, reason);
            return;
        }
        // PENDING이 아니면 CAS가 0건이고 상태별 코드로 거절된다
        orderService.cancelPendingOrder(orderId);
    }
}
