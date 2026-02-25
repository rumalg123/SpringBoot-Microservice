package com.rumal.review_service.repository;

import com.rumal.review_service.entity.VendorReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendorReplyRepository extends JpaRepository<VendorReply, UUID> {

    Optional<VendorReply> findByReview_Id(UUID reviewId);

    boolean existsByReview_Id(UUID reviewId);
}
