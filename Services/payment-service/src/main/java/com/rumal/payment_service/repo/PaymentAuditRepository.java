package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.PaymentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, UUID> {

    List<PaymentAudit> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    List<PaymentAudit> findByRefundRequestIdOrderByCreatedAtAsc(UUID refundRequestId);

    List<PaymentAudit> findByPayoutIdOrderByCreatedAtAsc(UUID payoutId);
}
