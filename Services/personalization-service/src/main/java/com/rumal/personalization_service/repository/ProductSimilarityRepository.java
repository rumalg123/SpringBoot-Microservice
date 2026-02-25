package com.rumal.personalization_service.repository;

import com.rumal.personalization_service.model.ProductSimilarity;
import com.rumal.personalization_service.model.ProductSimilarityId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductSimilarityRepository extends JpaRepository<ProductSimilarity, ProductSimilarityId> {

    List<ProductSimilarity> findByProductIdOrderByScoreDesc(UUID productId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProductSimilarity s WHERE s.lastComputedAt < :before")
    int deleteStaleEntries(Instant before);
}
