package com.rumal.review_service.service;

import com.rumal.review_service.dto.*;
import com.rumal.review_service.entity.ReportStatus;
import com.rumal.review_service.entity.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {
    ReviewResponse createReview(UUID customerId, String displayName, CreateReviewRequest request);
    ReviewResponse updateReview(UUID reviewId, UUID customerId, UpdateReviewRequest request);
    void deleteReview(UUID reviewId, UUID customerId);
    ReviewResponse getById(UUID id);
    Page<ReviewResponse> listByProduct(UUID productId, Pageable pageable, String sortBy);
    Page<ReviewResponse> listByCustomer(UUID customerId, Pageable pageable);
    Page<ReviewResponse> listByVendor(UUID vendorId, Pageable pageable);
    Page<ReviewResponse> adminList(Pageable pageable, UUID productId, UUID vendorId, UUID customerId, Integer rating, Boolean active);
    void adminDeactivate(UUID reviewId);
    void adminActivate(UUID reviewId);
    void adminDelete(UUID reviewId);
    ReviewSummaryResponse getProductReviewSummary(UUID productId);
    void vote(UUID reviewId, UUID userId, boolean helpful);
    void createReport(UUID reviewId, UUID reporterUserId, CreateReportRequest request);
    Page<ReviewReport> listReports(Pageable pageable, ReportStatus status);
    void updateReportStatus(UUID reportId, UpdateReportStatusRequest request);
    VendorReplyResponse createVendorReply(UUID reviewId, UUID vendorId, CreateVendorReplyRequest request);
    VendorReplyResponse updateVendorReply(UUID replyId, UUID vendorId, UpdateVendorReplyRequest request);
    void deleteVendorReply(UUID replyId, UUID vendorId);
}
