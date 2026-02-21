package com.rumal.wishlist_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
                @UniqueConstraint(name = "uk_wishlist_item_keycloak_product", columnNames = {"keycloak_id", "product_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, length = 120)
    private String keycloakId;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
