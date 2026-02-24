package com.rumal.product_service.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_vendor_id", columnList = "vendor_id"),
                @Index(name = "idx_products_product_type", columnList = "product_type"),
                @Index(name = "idx_products_active", columnList = "is_active"),
                @Index(name = "idx_products_deleted", columnList = "is_deleted"),
                @Index(name = "idx_products_sku", columnList = "sku"),
                @Index(name = "idx_products_slug", columnList = "slug"),
                @Index(name = "idx_products_approval_status", columnList = "approval_status"),
                @Index(name = "idx_products_view_count", columnList = "view_count"),
                @Index(name = "idx_products_sold_count", columnList = "sold_count"),
                @Index(name = "idx_products_vendor_active_deleted", columnList = "vendor_id, is_active, is_deleted"),
                @Index(name = "idx_products_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 180, unique = true)
    private String slug;

    @Column(name = "short_description", nullable = false, length = 300)
    private String shortDescription;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "meta_title", length = 70)
    private String metaTitle;

    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_name", nullable = false, length = 260)
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "regular_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "discounted_price", precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "parent_product_id")
    private UUID parentProductId;

    @ManyToMany
    @JoinTable(
            name = "product_category_map",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private Set<Category> categories = new java.util.LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @ElementCollection
    @CollectionTable(name = "product_variation_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @BatchSize(size = 50)
    @Builder.Default
    private List<ProductVariationAttribute> variations = new ArrayList<>();

    @Column(length = 80, unique = true, nullable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_digital", nullable = false)
    @Builder.Default
    private boolean digital = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_bundle_items", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "bundled_product_id")
    @BatchSize(size = 50)
    @Builder.Default
    private List<UUID> bundledProductIds = new ArrayList<>();

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0;

    @Column(name = "sold_count", nullable = false)
    @Builder.Default
    private long soldCount = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
