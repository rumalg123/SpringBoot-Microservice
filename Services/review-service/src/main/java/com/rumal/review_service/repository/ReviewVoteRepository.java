package com.rumal.review_service.repository;

import com.rumal.review_service.entity.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, UUID> {

    Optional<ReviewVote> findByReviewIdAndUserId(UUID reviewId, UUID userId);

    @Query("SELECT COUNT(v) FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.helpful = true")
    int countHelpfulByReviewId(@Param("reviewId") UUID reviewId);

    @Query("SELECT COUNT(v) FROM ReviewVote v WHERE v.reviewId = :reviewId AND v.helpful = false")
    int countNotHelpfulByReviewId(@Param("reviewId") UUID reviewId);
}
