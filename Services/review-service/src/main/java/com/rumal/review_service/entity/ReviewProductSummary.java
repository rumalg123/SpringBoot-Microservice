package com.rumal.review_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_product_summaries")
@Getter
@Setter
public class ReviewProductSummary {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "average_rating", nullable = false, precision = 4, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    private long totalReviews = 0L;

    @Column(name = "rating_1_count", nullable = false)
    private long rating1Count = 0L;

    @Column(name = "rating_2_count", nullable = false)
    private long rating2Count = 0L;

    @Column(name = "rating_3_count", nullable = false)
    private long rating3Count = 0L;

    @Column(name = "rating_4_count", nullable = false)
    private long rating4Count = 0L;

    @Column(name = "rating_5_count", nullable = false)
    private long rating5Count = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
