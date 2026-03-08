package com.rumal.review_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "review_summary_refresh_tasks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_summary_refresh_tasks_product", columnNames = "product_id")
        },
        indexes = {
                @Index(name = "idx_review_summary_refresh_tasks_status_due", columnList = "status,next_attempt_at,updated_at")
        }
)
@Getter
@Setter
public class ReviewSummaryRefreshTask {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewSummaryRefreshTaskStatus status = ReviewSummaryRefreshTaskStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
