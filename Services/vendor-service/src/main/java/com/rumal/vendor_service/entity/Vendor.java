package com.rumal.vendor_service.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "vendors",
        indexes = {
                @Index(name = "idx_vendors_slug", columnList = "slug"),
                @Index(name = "idx_vendors_active", columnList = "is_active"),
                @Index(name = "idx_vendors_deleted", columnList = "is_deleted"),
                @Index(name = "idx_vendors_status", columnList = "status"),
                @Index(name = "idx_vendors_normalized_name", columnList = "normalized_name"),
                @Index(name = "idx_vendors_verification_status", columnList = "verification_status"),
                @Index(name = "idx_vendors_primary_category", columnList = "primary_category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "contact_email", nullable = false, length = 180)
    private String contactEmail;

    @Column(name = "support_email", length = 180)
    private String supportEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "contact_person_name", length = 120)
    private String contactPersonName;

    @Column(name = "logo_image", length = 260)
    private String logoImage;

    @Column(name = "banner_image", length = 260)
    private String bannerImage;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VendorStatus status = VendorStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "accepting_orders", nullable = false)
    @Builder.Default
    private boolean acceptingOrders = true;

    // --- Gap 49: Verification ---
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_requested_at")
    private Instant verificationRequestedAt;

    @Column(name = "verification_notes", length = 1000)
    private String verificationNotes;

    @Column(name = "verification_document_url", length = 500)
    private String verificationDocumentUrl;

    // --- Gap 50: Performance Metrics ---
    @Column(name = "average_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_ratings", nullable = false)
    @Builder.Default
    private int totalRatings = 0;

    @Column(name = "fulfillment_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal fulfillmentRate = BigDecimal.ZERO;

    @Column(name = "dispute_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal disputeRate = BigDecimal.ZERO;

    @Column(name = "response_time_hours", precision = 7, scale = 2)
    @Builder.Default
    private BigDecimal responseTimeHours = BigDecimal.ZERO;

    @Column(name = "total_orders_completed", nullable = false)
    @Builder.Default
    private int totalOrdersCompleted = 0;

    // --- Gap 51: Return/Shipping Policy ---
    @Column(name = "return_policy", columnDefinition = "TEXT")
    private String returnPolicy;

    @Column(name = "shipping_policy", columnDefinition = "TEXT")
    private String shippingPolicy;

    @Column(name = "processing_time_days", nullable = false)
    @Builder.Default
    private int processingTimeDays = 0;

    @Column(name = "accepts_returns", nullable = false)
    @Builder.Default
    private boolean acceptsReturns = false;

    @Column(name = "return_window_days", nullable = false)
    @Builder.Default
    private int returnWindowDays = 0;

    @Column(name = "free_shipping_threshold", precision = 19, scale = 2)
    private BigDecimal freeShippingThreshold;

    // --- Gap 52: Categories/Specializations ---
    @Column(name = "primary_category", length = 100)
    private String primaryCategory;

    @ElementCollection
    @CollectionTable(name = "vendor_specializations", joinColumns = @JoinColumn(name = "vendor_id"))
    @Column(name = "specialization", length = 100)
    @BatchSize(size = 20)
    @Builder.Default
    private Set<String> specializations = new LinkedHashSet<>();

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "deletion_request_reason", length = 500)
    private String deletionRequestReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
