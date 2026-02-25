package com.rumal.payment_service.service;

import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentAuditService {

    private final PaymentAuditRepository auditRepository;

    public void writeAudit(UUID paymentId, UUID refundRequestId, UUID payoutId,
                           String eventType, String fromStatus, String toStatus,
                           String actorType, String actorId, String note, String rawPayload) {
        PaymentAudit audit = PaymentAudit.builder()
                .paymentId(paymentId)
                .refundRequestId(refundRequestId)
                .payoutId(payoutId)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorType(actorType)
                .actorId(actorId)
                .note(note)
                .rawPayload(rawPayload)
                .build();
        auditRepository.save(audit);
    }
}
