package com.rumal.poster_service.repo;

import com.rumal.poster_service.entity.Poster;
import com.rumal.poster_service.entity.PosterPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosterRepository extends JpaRepository<Poster, UUID> {
    Optional<Poster> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Poster> findByDeletedFalseAndPlacementOrderBySortOrderAscCreatedAtDesc(PosterPlacement placement);
    List<Poster> findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc();
    List<Poster> findByDeletedTrueOrderByUpdatedAtDesc();
}
