package com.rumal.review_service.service;

import com.rumal.review_service.dto.analytics.*;
import com.rumal.review_service.repository.ReviewRepository;
import com.rumal.review_service.repository.VendorReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class ReviewAnalyticsService {

    private final ReviewRepository reviewRepository;
    private final VendorReplyRepository vendorReplyRepository;

    public ReviewPlatformSummary getPlatformSummary() {
        long active = reviewRepository.countActiveReviews();
        double avgRating = reviewRepository.avgActiveRating();
        long verified = reviewRepository.countVerifiedPurchaseReviews();
        long reported = reviewRepository.countReportedReviews();

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        long thisMonth = reviewRepository.countActiveReviewsSince(startOfMonth);

        double verifiedPercent = active > 0 ? (double) verified / active * 100.0 : 0.0;

        return new ReviewPlatformSummary(active, active,
            Math.round(avgRating * 100.0) / 100.0,
            Math.round(verifiedPercent * 100.0) / 100.0,
            reported, thisMonth);
    }

    public Map<Integer, Long> getRatingDistribution() {
        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        for (Object[] row : reviewRepository.getRatingDistribution()) {
            dist.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return dist;
    }

    public VendorReviewSummary getVendorSummary(UUID vendorId) {
        long total = reviewRepository.countActiveByVendorId(vendorId);
        double avgRating = reviewRepository.avgRatingByVendorId(vendorId);
        long verified = reviewRepository.countVerifiedPurchaseByVendorId(vendorId);
        long replies = vendorReplyRepository.countByVendorId(vendorId);

        Map<Integer, Long> ratingDist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) ratingDist.put(i, 0L);
        for (Object[] row : reviewRepository.getRatingDistributionByVendorId(vendorId)) {
            ratingDist.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        double verifiedPercent = total > 0 ? (double) verified / total * 100.0 : 0.0;
        double replyRate = total > 0 ? (double) replies / total * 100.0 : 0.0;

        return new VendorReviewSummary(vendorId, total,
            Math.round(avgRating * 100.0) / 100.0,
            ratingDist,
            Math.round(verifiedPercent * 100.0) / 100.0,
            Math.round(replyRate * 100.0) / 100.0);
    }
}
