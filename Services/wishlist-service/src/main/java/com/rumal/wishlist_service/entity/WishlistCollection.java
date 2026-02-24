package com.rumal.wishlist_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "wishlist_collections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_collection_share_token", columnNames = {"share_token"})
        },
        indexes = {
                @Index(name = "idx_collections_keycloak_id", columnList = "keycloak_id"),
                @Index(name = "idx_collections_share_token", columnList = "share_token")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistCollection {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, length = 120)
    private String keycloakId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "shared", nullable = false)
    @Builder.Default
    private boolean shared = false;

    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
