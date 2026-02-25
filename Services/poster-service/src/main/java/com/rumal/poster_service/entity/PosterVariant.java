package com.rumal.poster_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
        name = "poster_variants",
        indexes = {
                @Index(name = "idx_poster_variants_poster_id", columnList = "poster_id"),
                @Index(name = "idx_poster_variants_active", columnList = "is_active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosterVariant {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poster_id", nullable = false)
    private Poster poster;

    @Column(name = "variant_name", nullable = false, length = 160)
    private String variantName;

    @Column(nullable = false)
    @Builder.Default
    private int weight = 50;

    @Column(name = "desktop_image", length = 260)
    private String desktopImage;

    @Column(name = "mobile_image", length = 260)
    private String mobileImage;

    @Column(name = "tablet_image", length = 260)
    private String tabletImage;

    @Column(name = "srcset_desktop", length = 1000)
    private String srcsetDesktop;

    @Column(name = "srcset_mobile", length = 1000)
    private String srcsetMobile;

    @Column(name = "srcset_tablet", length = 1000)
    private String srcsetTablet;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "impression_count", nullable = false)
    @Builder.Default
    private long impressions = 0;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private long clicks = 0;

    @Version
    private Long version;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
