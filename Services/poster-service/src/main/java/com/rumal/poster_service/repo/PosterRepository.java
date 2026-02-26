package com.rumal.poster_service.repo;

import com.rumal.poster_service.entity.Poster;
import com.rumal.poster_service.entity.PosterPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosterRepository extends JpaRepository<Poster, UUID> {
    Optional<Poster> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Poster> findByDeletedFalseAndPlacementOrderBySortOrderAscCreatedAtDesc(PosterPlacement placement);
    List<Poster> findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc();
    Page<Poster> findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc(Pageable pageable);
    Page<Poster> findByDeletedTrueOrderByUpdatedAtDesc(Pageable pageable);
    Page<Poster> findByDeletedFalseAndPlacementOrderBySortOrderAscCreatedAtDesc(PosterPlacement placement, Pageable pageable);

    @Query("""
            SELECT p FROM Poster p
            WHERE p.deleted = false AND p.active = true AND p.placement = :placement
              AND (p.startAt IS NULL OR p.startAt <= :now)
              AND (p.endAt IS NULL OR p.endAt >= :now)
            ORDER BY p.sortOrder ASC, p.createdAt DESC
            """)
    Page<Poster> findActiveByPlacementInWindow(@Param("placement") PosterPlacement placement, @Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT p FROM Poster p
            WHERE p.deleted = false AND p.active = true
              AND (p.startAt IS NULL OR p.startAt <= :now)
              AND (p.endAt IS NULL OR p.endAt >= :now)
            ORDER BY p.placement ASC, p.sortOrder ASC, p.createdAt DESC
            """)
    Page<Poster> findAllActiveInWindow(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE Poster p SET p.clickCount = p.clickCount + 1, p.lastClickAt = :now WHERE p.id = :id AND p.deleted = false")
    int incrementClickCount(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Poster p SET p.impressionCount = p.impressionCount + 1, p.lastImpressionAt = :now WHERE p.id = :id AND p.deleted = false")
    int incrementImpressionCount(@Param("id") UUID id, @Param("now") Instant now);

    List<Poster> findByDeletedFalseOrderByClickCountDesc();
}
