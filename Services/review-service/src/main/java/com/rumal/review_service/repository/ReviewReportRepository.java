package com.rumal.review_service.repository;

import com.rumal.review_service.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID>, JpaSpecificationExecutor<ReviewReport> {

    boolean existsByReviewIdAndReporterUserId(UUID reviewId, UUID reporterUserId);
}
