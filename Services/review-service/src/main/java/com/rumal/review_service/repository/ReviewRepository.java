package com.rumal.review_service.repository;

import com.rumal.review_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    Optional<Review> findByCustomerIdAndProductIdAndDeletedFalse(UUID customerId, UUID productId);

    boolean existsByCustomerIdAndProductIdAndDeletedFalse(UUID customerId, UUID productId);

    @Query("SELECT r.rating AS rating, COUNT(r) AS cnt FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true GROUP BY r.rating")
    List<Object[]> countByProductIdGroupByRating(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true")
    double averageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true")
    long countActiveByProductId(@Param("productId") UUID productId);
}
