package com.rumal.review_service.repository;

import com.rumal.review_service.entity.ReviewProductSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewProductSummaryRepository extends JpaRepository<ReviewProductSummary, UUID> {
}
