package com.rumal.poster_service.repo;

import com.rumal.poster_service.entity.PosterVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PosterVariantRepository extends JpaRepository<PosterVariant, UUID> {

    List<PosterVariant> findByPosterIdOrderByCreatedAtAsc(UUID posterId);

    List<PosterVariant> findByPosterIdInOrderByCreatedAtAsc(Collection<UUID> posterIds);

    List<PosterVariant> findByPosterIdAndActiveTrueOrderByCreatedAtAsc(UUID posterId);

    @Modifying
    @Query("UPDATE PosterVariant v SET v.clicks = v.clicks + 1 WHERE v.id = :variantId AND v.active = true")
    int incrementClickCount(@Param("variantId") UUID variantId);

    @Modifying
    @Query("UPDATE PosterVariant v SET v.impressions = v.impressions + 1 WHERE v.id = :variantId AND v.active = true")
    int incrementImpressionCount(@Param("variantId") UUID variantId);

    void deleteAllByPosterId(UUID posterId);
}
