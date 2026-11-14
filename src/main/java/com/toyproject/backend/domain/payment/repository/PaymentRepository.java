package com.toyproject.backend.domain.payment.repository;

import com.toyproject.backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

/** [Phase 4 · 9단계] 결제 저장소. 조회 메서드는 쓰는 단계에서 더한다. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
