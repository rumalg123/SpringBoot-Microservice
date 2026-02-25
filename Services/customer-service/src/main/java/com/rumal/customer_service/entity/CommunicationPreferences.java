package com.rumal.customer_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "communication_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_comm_prefs_customer_id", columnNames = "customer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunicationPreferences {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "email_marketing", nullable = false)
    @Builder.Default
    private boolean emailMarketing = false;

    @Column(name = "sms_marketing", nullable = false)
    @Builder.Default
    private boolean smsMarketing = false;

    @Column(name = "push_notifications", nullable = false)
    @Builder.Default
    private boolean pushNotifications = false;

    @Column(name = "order_updates", nullable = false)
    @Builder.Default
    private boolean orderUpdates = true;

    @Column(name = "promotional_alerts", nullable = false)
    @Builder.Default
    private boolean promotionalAlerts = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }
}
