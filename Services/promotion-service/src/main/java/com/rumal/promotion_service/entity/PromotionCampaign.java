package com.rumal.promotion_service.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "promotion_campaigns",
        indexes = {
                @Index(name = "idx_promo_campaign_vendor", columnList = "vendor_id"),
                @Index(name = "idx_promo_campaign_lifecycle", columnList = "lifecycle_status"),
                @Index(name = "idx_promo_campaign_approval", columnList = "approval_status"),
                @Index(name = "idx_promo_campaign_scope", columnList = "scope_type"),
                @Index(name = "idx_promo_campaign_starts_at", columnList = "starts_at"),
                @Index(name = "idx_promo_campaign_ends_at", columnList = "ends_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionCampaign {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 1500)
    private String description;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private PromotionScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_level", nullable = false, length = 20)
    private PromotionApplicationLevel applicationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 30)
    private PromotionBenefitType benefitType;

    @Column(name = "benefit_value", precision = 19, scale = 2)
    private BigDecimal benefitValue;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @ElementCollection
    @CollectionTable(name = "promotion_spend_tiers", joinColumns = @JoinColumn(name = "promotion_id"))
    @OrderColumn(name = "tier_order_index")
    @BatchSize(size = 50)
    @Builder.Default
    private List<PromotionSpendTier> spendTiers = new ArrayList<>();

    @Column(name = "minimum_order_amount", precision = 19, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(name = "maximum_discount_amount", precision = 19, scale = 2)
    private BigDecimal maximumDiscountAmount;

    @Column(name = "budget_amount", precision = 19, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "burned_budget_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal burnedBudgetAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 20)
    private PromotionFundingSource fundingSource;

    @Column(name = "is_stackable", nullable = false)
    @Builder.Default
    private boolean stackable = false;

    @Column(name = "is_exclusive", nullable = false)
    @Builder.Default
    private boolean exclusive = false;

    @Column(name = "is_auto_apply", nullable = false)
    @Builder.Default
    private boolean autoApply = true;

    @Column(name = "priority_rank", nullable = false)
    @Builder.Default
    private int priority = 100;

    @ElementCollection
    @CollectionTable(name = "promotion_target_product_ids", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "product_id", nullable = false)
    @BatchSize(size = 50)
    @Builder.Default
    private Set<UUID> targetProductIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "promotion_target_category_ids", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "category_id", nullable = false)
    @BatchSize(size = 50)
    @Builder.Default
    private Set<UUID> targetCategoryIds = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private PromotionLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private PromotionApprovalStatus approvalStatus;

    @Column(name = "approval_note", length = 1000)
    private String approvalNote;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by_user_sub", length = 120)
    private String createdByUserSub;

    @Column(name = "updated_by_user_sub", length = 120)
    private String updatedByUserSub;

    @Column(name = "submitted_by_user_sub", length = 120)
    private String submittedByUserSub;

    @Column(name = "approved_by_user_sub", length = 120)
    private String approvedByUserSub;

    @Column(name = "rejected_by_user_sub", length = 120)
    private String rejectedByUserSub;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
