package com.rumal.personalization_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_event", indexes = {
        @Index(name = "idx_user_event_user_type_created", columnList = "user_id, event_type, created_at"),
        @Index(name = "idx_user_event_session_created", columnList = "session_id, created_at"),
        @Index(name = "idx_user_event_session_type_created", columnList = "session_id, event_type, created_at"),
        @Index(name = "idx_user_event_type_user_created", columnList = "event_type, user_id, created_at"),
        @Index(name = "idx_user_event_product_type_created", columnList = "product_id, event_type, created_at"),
        @Index(name = "idx_user_event_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @Column(length = 64)
    private String sessionId;

    @Column(length = 32, nullable = false)
    private String eventType;

    @Column(nullable = false)
    private UUID productId;

    @Column(length = 500)
    private String categorySlugs;

    private UUID vendorId;

    @Column(length = 255)
    private String brandName;

    private BigDecimal price;

    @Column(length = 1000)
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
