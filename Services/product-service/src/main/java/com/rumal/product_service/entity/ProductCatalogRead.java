package com.rumal.product_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "product_catalog_read",
        indexes = {
                @Index(name = "idx_catalog_read_slug", columnList = "slug"),
                @Index(name = "idx_catalog_read_sku", columnList = "sku"),
                @Index(name = "idx_catalog_read_type", columnList = "product_type"),
                @Index(name = "idx_catalog_read_active", columnList = "is_active"),
                @Index(name = "idx_catalog_read_deleted", columnList = "is_deleted"),
                @Index(name = "idx_catalog_read_vendor", columnList = "vendor_id"),
                @Index(name = "idx_catalog_read_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCatalogRead {

    @Id
    private UUID id;

    @Column(name = "parent_product_id")
    private UUID parentProductId;

    @Column(nullable = false, length = 180, unique = true)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "short_description", nullable = false, length = 300)
    private String shortDescription;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "main_image", length = 260)
    private String mainImage;

    @Column(name = "regular_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "discounted_price", precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false, length = 80, unique = true)
    private String sku;

    @Column(name = "main_category", length = 100)
    private String mainCategory;

    @Column(name = "main_category_slug", length = 130)
    private String mainCategorySlug;

    @Column(name = "sub_category_tokens", length = 3000)
    private String subCategoryTokens;

    @Column(name = "sub_category_tokens_lc", length = 3000)
    private String subCategoryTokensLc;

    @Column(name = "category_tokens", length = 3000)
    private String categoryTokens;

    @Column(name = "category_tokens_lc", length = 3000)
    private String categoryTokensLc;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "has_active_variation_child", nullable = false)
    private boolean hasActiveVariationChild;

    @Column(name = "name_lc", nullable = false, length = 150)
    private String nameLc;

    @Column(name = "short_description_lc", nullable = false, length = 300)
    private String shortDescriptionLc;

    @Column(name = "description_lc", nullable = false, length = 4000)
    private String descriptionLc;

    @Column(name = "sku_lc", nullable = false, length = 80)
    private String skuLc;

    @Column(name = "main_category_lc", length = 100)
    private String mainCategoryLc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
