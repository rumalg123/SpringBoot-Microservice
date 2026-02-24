package com.rumal.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_audits", indexes = {
        @Index(name = "idx_audit_payment_id", columnList = "payment_id"),
        @Index(name = "idx_audit_refund_id", columnList = "refund_request_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "refund_request_id")
    private UUID refundRequestId;

    @Column(name = "payout_id")
    private UUID payoutId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "actor_id", length = 120)
    private String actorId;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
