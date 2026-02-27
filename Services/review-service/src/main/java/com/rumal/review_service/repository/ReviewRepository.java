package com.rumal.review_service.repository;

import com.rumal.review_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    Optional<Review> findByCustomerIdAndProductIdAndDeletedFalse(UUID customerId, UUID productId);

    boolean existsByCustomerIdAndProductIdAndDeletedFalse(UUID customerId, UUID productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.customerId = :customerId AND r.productId = :productId AND r.deleted = false")
    Optional<Review> findByCustomerIdAndProductIdForUpdate(@Param("customerId") UUID customerId, @Param("productId") UUID productId);

    @Query("SELECT r.rating AS rating, COUNT(r) AS cnt FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true GROUP BY r.rating")
    List<Object[]> countByProductIdGroupByRating(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true")
    double averageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId AND r.deleted = false AND r.active = true")
    long countActiveByProductId(@Param("productId") UUID productId);

    // --- Analytics queries ---

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false AND r.active = true")
    long countActiveReviews();

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false")
    long countTotalReviews();

    @Query("SELECT COALESCE(AVG(CAST(r.rating AS double)), 0) FROM Review r WHERE r.deleted = false AND r.active = true")
    double avgActiveRating();

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false AND r.active = true AND r.verifiedPurchase = true")
    long countVerifiedPurchaseReviews();

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false AND r.reportCount > 0")
    long countReportedReviews();

    @Query("SELECT COUNT(r) FROM Review r WHERE r.deleted = false AND r.active = true AND r.createdAt >= :since")
    long countActiveReviewsSince(@Param("since") java.time.Instant since);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.deleted = false AND r.active = true GROUP BY r.rating ORDER BY r.rating")
    List<Object[]> getRatingDistribution();

    // Vendor-specific
    @Query("SELECT COUNT(r) FROM Review r WHERE r.vendorId = :vendorId AND r.deleted = false AND r.active = true")
    long countActiveByVendorId(@Param("vendorId") java.util.UUID vendorId);

    @Query("SELECT COALESCE(AVG(CAST(r.rating AS double)), 0) FROM Review r WHERE r.vendorId = :vendorId AND r.deleted = false AND r.active = true")
    double avgRatingByVendorId(@Param("vendorId") java.util.UUID vendorId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.vendorId = :vendorId AND r.deleted = false AND r.active = true GROUP BY r.rating ORDER BY r.rating")
    List<Object[]> getRatingDistributionByVendorId(@Param("vendorId") java.util.UUID vendorId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.vendorId = :vendorId AND r.deleted = false AND r.active = true AND r.verifiedPurchase = true")
    long countVerifiedPurchaseByVendorId(@Param("vendorId") java.util.UUID vendorId);

    // --- Atomic update queries to avoid race conditions on denormalized counts ---

    @Modifying
    @Query("UPDATE Review r SET r.helpfulCount = (SELECT COUNT(v) FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.helpful = true), r.notHelpfulCount = (SELECT COUNT(v) FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.helpful = false) WHERE r.id = :reviewId")
    void recalculateVoteCounts(@Param("reviewId") UUID reviewId);

    @Modifying
    @Query("UPDATE Review r SET r.reportCount = r.reportCount + 1 WHERE r.id = :reviewId")
    void incrementReportCount(@Param("reviewId") UUID reviewId);
}
