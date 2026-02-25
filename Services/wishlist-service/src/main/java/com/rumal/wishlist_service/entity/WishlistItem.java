package com.rumal.wishlist_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "wishlist_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wishlist_item_collection_product", columnNames = {"collection_id", "product_id"})
        },
        indexes = {
                @Index(name = "idx_wishlist_items_keycloak_id", columnList = "keycloak_id"),
                @Index(name = "idx_wishlist_items_collection_id", columnList = "collection_id"),
                @Index(name = "idx_wishlist_items_keycloak_product", columnList = "keycloak_id, product_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, length = 120)
    private String keycloakId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wishlist_item_collection"))
    private WishlistCollection collection;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_slug", nullable = false, length = 180)
    private String productSlug;

    @Column(name = "product_name", nullable = false, length = 180)
    private String productName;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType;

    @Column(name = "main_image", length = 300)
    private String mainImage;

    @Column(name = "selling_price_snapshot", precision = 19, scale = 2)
    private BigDecimal sellingPriceSnapshot;

    @Column(name = "note", length = 500)
    private String note;

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
