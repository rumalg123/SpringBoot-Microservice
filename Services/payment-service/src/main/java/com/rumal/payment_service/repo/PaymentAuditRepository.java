package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.PaymentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, UUID> {

    List<PaymentAudit> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    Page<PaymentAudit> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId, Pageable pageable);

    List<PaymentAudit> findByRefundRequestIdOrderByCreatedAtAsc(UUID refundRequestId);

    List<PaymentAudit> findByPayoutIdOrderByCreatedAtAsc(UUID payoutId);
}
