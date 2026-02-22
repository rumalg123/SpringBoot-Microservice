package com.rumal.vendor_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
        name = "vendors",
        indexes = {
                @Index(name = "idx_vendors_slug", columnList = "slug"),
                @Index(name = "idx_vendors_active", columnList = "is_active"),
                @Index(name = "idx_vendors_deleted", columnList = "is_deleted"),
                @Index(name = "idx_vendors_status", columnList = "status"),
                @Index(name = "idx_vendors_normalized_name", columnList = "normalized_name")
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

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VendorStatus status = VendorStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
