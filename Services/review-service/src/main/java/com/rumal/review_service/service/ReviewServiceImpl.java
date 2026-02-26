package com.rumal.review_service.service;

import com.rumal.review_service.client.OrderPurchaseVerificationClient;
import com.rumal.review_service.dto.*;
import com.rumal.review_service.entity.*;
import com.rumal.review_service.exception.ResourceNotFoundException;
import com.rumal.review_service.exception.ValidationException;
import com.rumal.review_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final ReviewRepository reviewRepository;
    private final VendorReplyRepository vendorReplyRepository;
    private final ReviewVoteRepository reviewVoteRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final OrderPurchaseVerificationClient orderPurchaseVerificationClient;
    private final ReviewCacheVersionService reviewCacheVersionService;

    // self-invocation field removed (unused)

    // ─── Create ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ReviewResponse createReview(UUID customerId, String displayName, CreateReviewRequest request) {
        // Check if already reviewed
        if (reviewRepository.existsByCustomerIdAndProductIdAndDeletedFalse(customerId, request.productId())) {
            throw new ValidationException("You have already reviewed this product");
        }

        // Verify purchase
        CustomerProductPurchaseCheckResponse purchaseCheck =
                orderPurchaseVerificationClient.checkPurchase(customerId, request.productId());
        if (!purchaseCheck.purchased()) {
            throw new ValidationException("You can only review products you have purchased and received");
        }

        Review review = Review.builder()
                .customerId(customerId)
                .customerDisplayName(displayName)
                .productId(request.productId())
                .vendorId(request.vendorId())
                .orderId(purchaseCheck.orderId())
                .rating(request.rating())
                .title(request.title())
                .comment(request.comment())
                .images(request.images() != null ? new ArrayList<>(request.images()) : new ArrayList<>())
                .verifiedPurchase(true)
                .build();

        review = reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
        return toResponse(review);
    }

    // ─── Update ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ReviewResponse updateReview(UUID reviewId, UUID customerId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!review.getCustomerId().equals(customerId)) {
            throw new ValidationException("You can only edit your own reviews");
        }
        if (review.isDeleted()) {
            throw new ResourceNotFoundException("Review not found");
        }

        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setComment(request.comment());
        if (request.images() != null) {
            review.setImages(new ArrayList<>(request.images()));
        }
        review = reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
        return toResponse(review);
    }

    // ─── Delete ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deleteReview(UUID reviewId, UUID customerId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (!review.getCustomerId().equals(customerId)) {
            throw new ValidationException("You can only delete your own reviews");
        }
        review.setDeleted(true);
        review.setDeletedAt(Instant.now());
        reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    // ─── Get by ID ──────────────────────────────────────────
    @Override
    @Cacheable(cacheNames = "reviewById",
            key = "@reviewCacheVersionService.reviewByIdVersion() + '::id::' + #id")
    public ReviewResponse getById(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.isDeleted()) throw new ResourceNotFoundException("Review not found");
        return toResponse(review);
    }

    // ─── List by product ────────────────────────────────────
    @Override
    @Cacheable(cacheNames = "reviewsByProduct",
            key = "@reviewCacheVersionService.reviewsByProductVersion() + '::' + " +
                    "T(com.rumal.review_service.service.ReviewServiceImpl).listByProductCacheKey(#productId, #pageable, #sortBy)")
    public Page<ReviewResponse> listByProduct(UUID productId, Pageable pageable, String sortBy) {
        Sort sort = resolveSort(sortBy);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Specification<Review> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("productId"), productId),
                cb.isFalse(root.get("deleted")),
                cb.isTrue(root.get("active"))
        );

        return reviewRepository.findAll(spec, sortedPageable).map(this::toResponse);
    }

    public static String listByProductCacheKey(UUID productId, Pageable pageable, String sortBy) {
        return productId + "::" + pageable.getPageNumber() + "::" + pageable.getPageSize() + "::" + (sortBy != null ? sortBy : "recent");
    }

    // ─── List by customer ───────────────────────────────────
    @Override
    public Page<ReviewResponse> listByCustomer(UUID customerId, Pageable pageable) {
        Specification<Review> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId),
                cb.isFalse(root.get("deleted"))
        );
        return reviewRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // ─── List by vendor ─────────────────────────────────────
    @Override
    public Page<ReviewResponse> listByVendor(UUID vendorId, Pageable pageable) {
        Specification<Review> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("vendorId"), vendorId),
                cb.isFalse(root.get("deleted")),
                cb.isTrue(root.get("active"))
        );
        return reviewRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // ─── Admin list ─────────────────────────────────────────
    @Override
    public Page<ReviewResponse> adminList(Pageable pageable, UUID productId, UUID vendorId,
                                           UUID customerId, Integer rating, Boolean active) {
        Specification<Review> spec = (root, query, cb) -> cb.isFalse(root.get("deleted"));

        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productId"), productId));
        }
        if (vendorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vendorId"), vendorId));
        }
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        if (rating != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rating"), rating));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        return reviewRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // ─── Admin actions ──────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void adminDeactivate(UUID reviewId) {
        Review review = findActiveReview(reviewId);
        review.setActive(false);
        reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void adminActivate(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.isDeleted()) throw new ResourceNotFoundException("Review not found");
        if (review.isActive()) throw new ValidationException("Review is already active");
        review.setActive(true);
        reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void adminDelete(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setDeleted(true);
        review.setDeletedAt(Instant.now());
        reviewRepository.save(review);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    // ─── Summary ────────────────────────────────────────────
    @Override
    @Cacheable(cacheNames = "reviewSummary",
            key = "@reviewCacheVersionService.reviewSummaryVersion() + '::product::' + #productId")
    public ReviewSummaryResponse getProductReviewSummary(UUID productId) {
        double avg = reviewRepository.averageRatingByProductId(productId);
        long total = reviewRepository.countActiveByProductId(productId);
        List<Object[]> distribution = reviewRepository.countByProductIdGroupByRating(productId);

        Map<Integer, Long> ratingMap = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) ratingMap.put(i, 0L);
        for (Object[] row : distribution) {
            int rating = (Integer) row[0];
            long count = (Long) row[1];
            ratingMap.put(rating, count);
        }

        return new ReviewSummaryResponse(productId, Math.round(avg * 10.0) / 10.0, total, ratingMap);
    }

    // ─── Voting ─────────────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void vote(UUID reviewId, UUID userId, boolean helpful) {
        Review review = findActiveReview(reviewId);

        Optional<ReviewVote> existingVote = reviewVoteRepository.findByReviewIdAndUserId(reviewId, userId);
        if (existingVote.isPresent()) {
            ReviewVote vote = existingVote.get();
            if (vote.isHelpful() == helpful) {
                // Same vote again — remove it (toggle off)
                reviewVoteRepository.delete(vote);
            } else {
                // Change vote direction
                vote.setHelpful(helpful);
                reviewVoteRepository.save(vote);
            }
        } else {
            ReviewVote vote = ReviewVote.builder()
                    .reviewId(reviewId)
                    .userId(userId)
                    .helpful(helpful)
                    .build();
            reviewVoteRepository.save(vote);
        }

        // Recalculate denormalized counts atomically
        reviewRepository.recalculateVoteCounts(reviewId);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    // ─── Reporting ──────────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void createReport(UUID reviewId, UUID reporterUserId, CreateReportRequest request) {
        findActiveReview(reviewId); // ensure review exists

        if (reviewReportRepository.existsByReviewIdAndReporterUserId(reviewId, reporterUserId)) {
            throw new ValidationException("You have already reported this review");
        }

        ReviewReport report = ReviewReport.builder()
                .reviewId(reviewId)
                .reporterUserId(reporterUserId)
                .reason(request.reason())
                .description(request.description())
                .build();
        reviewReportRepository.save(report);

        // Increment denormalized report count atomically
        reviewRepository.incrementReportCount(reviewId);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    @Override
    public Page<ReviewReport> listReports(Pageable pageable, ReportStatus status) {
        if (status != null) {
            Specification<ReviewReport> spec = (root, query, cb) -> cb.equal(root.get("status"), status);
            return reviewReportRepository.findAll(spec, pageable);
        }
        return reviewReportRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void updateReportStatus(UUID reportId, UpdateReportStatusRequest request) {
        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        report.setStatus(request.status());
        report.setAdminNotes(request.adminNotes());
        reviewReportRepository.save(report);
    }

    // ─── Vendor Reply ───────────────────────────────────────
    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorReplyResponse createVendorReply(UUID reviewId, UUID vendorId, CreateVendorReplyRequest request) {
        Review review = findActiveReview(reviewId);

        if (!review.getVendorId().equals(vendorId)) {
            throw new ValidationException("You can only reply to reviews on your own products");
        }
        if (vendorReplyRepository.existsByReview_Id(reviewId)) {
            throw new ValidationException("A reply already exists for this review");
        }

        VendorReply reply = VendorReply.builder()
                .review(review)
                .vendorId(vendorId)
                .comment(request.comment())
                .build();
        reply = vendorReplyRepository.save(reply);
        reviewCacheVersionService.bumpAllReviewCaches();
        return toReplyResponse(reply);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorReplyResponse updateVendorReply(UUID replyId, UUID vendorId, UpdateVendorReplyRequest request) {
        VendorReply reply = vendorReplyRepository.findById(replyId)
                .orElseThrow(() -> new ResourceNotFoundException("Reply not found"));
        if (!reply.getVendorId().equals(vendorId)) {
            throw new ValidationException("You can only edit your own replies");
        }
        reply.setComment(request.comment());
        reply = vendorReplyRepository.save(reply);
        reviewCacheVersionService.bumpAllReviewCaches();
        return toReplyResponse(reply);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deleteVendorReply(UUID replyId, UUID vendorId) {
        VendorReply reply = vendorReplyRepository.findById(replyId)
                .orElseThrow(() -> new ResourceNotFoundException("Reply not found"));
        if (!reply.getVendorId().equals(vendorId)) {
            throw new ValidationException("You can only delete your own replies");
        }
        vendorReplyRepository.delete(reply);
        reviewCacheVersionService.bumpAllReviewCaches();
    }

    // ─── Helpers ────────────────────────────────────────────
    private Review findActiveReview(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.isDeleted() || !review.isActive()) {
            throw new ResourceNotFoundException("Review not found");
        }
        return review;
    }

    private ReviewResponse toResponse(Review review) {
        VendorReplyResponse replyResponse = null;
        VendorReply reply = review.getVendorReply();
        if (reply != null) {
            replyResponse = toReplyResponse(reply);
        }
        return new ReviewResponse(
                review.getId(),
                review.getCustomerId(),
                review.getCustomerDisplayName(),
                review.getProductId(),
                review.getVendorId(),
                review.getOrderId(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getImages() != null ? List.copyOf(review.getImages()) : List.of(),
                review.getHelpfulCount(),
                review.getNotHelpfulCount(),
                review.isVerifiedPurchase(),
                review.isActive(),
                replyResponse,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private VendorReplyResponse toReplyResponse(VendorReply reply) {
        return new VendorReplyResponse(
                reply.getId(),
                reply.getVendorId(),
                reply.getComment(),
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }

    private Sort resolveSort(String sortBy) {
        if (sortBy == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sortBy) {
            case "helpful" -> Sort.by(Sort.Direction.DESC, "helpfulCount");
            case "rating_high" -> Sort.by(Sort.Direction.DESC, "rating");
            case "rating_low" -> Sort.by(Sort.Direction.ASC, "rating");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
