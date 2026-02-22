package com.rumal.poster_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "posters",
        indexes = {
                @Index(name = "idx_posters_slug", columnList = "slug"),
                @Index(name = "idx_posters_placement", columnList = "placement"),
                @Index(name = "idx_posters_active", columnList = "is_active"),
                @Index(name = "idx_posters_deleted", columnList = "is_deleted"),
                @Index(name = "idx_posters_sort_order", columnList = "sort_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Poster {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PosterPlacement placement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosterSize size;

    @Column(name = "desktop_image", nullable = false, length = 260)
    private String desktopImage;

    @Column(name = "mobile_image", length = 260)
    private String mobileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private PosterLinkType linkType;

    @Column(name = "link_target", length = 500)
    private String linkTarget;

    @Column(name = "open_in_new_tab", nullable = false)
    @Builder.Default
    private boolean openInNewTab = false;

    @Column(length = 120)
    private String title;

    @Column(length = 250)
    private String subtitle;

    @Column(name = "cta_label", length = 60)
    private String ctaLabel;

    @Column(name = "background_color", length = 40)
    private String backgroundColor;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

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
}
