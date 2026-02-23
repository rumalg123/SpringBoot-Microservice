package com.rumal.promotion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "coupon_codes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_coupon_codes_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_coupon_codes_promotion", columnList = "promotion_id"),
                @Index(name = "idx_coupon_codes_active", columnList = "is_active"),
                @Index(name = "idx_coupon_codes_starts_at", columnList = "starts_at"),
                @Index(name = "idx_coupon_codes_ends_at", columnList = "ends_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponCode {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private PromotionCampaign promotion;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "max_uses_per_customer")
    private Integer maxUsesPerCustomer;

    @Column(name = "reservation_ttl_seconds")
    private Integer reservationTtlSeconds;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by_user_sub", length = 120)
    private String createdByUserSub;

    @Column(name = "updated_by_user_sub", length = 120)
    private String updatedByUserSub;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
