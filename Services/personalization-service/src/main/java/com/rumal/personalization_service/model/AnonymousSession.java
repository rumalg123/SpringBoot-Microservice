package com.rumal.personalization_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anonymous_session", indexes = {
        @Index(name = "idx_anon_session_merged_activity", columnList = "merged_at, last_activity_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnonymousSession {

    @Id
    @Column(length = 64)
    private String sessionId;

    private UUID userId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastActivityAt;

    private Instant mergedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastActivityAt == null) lastActivityAt = now;
    }
}
