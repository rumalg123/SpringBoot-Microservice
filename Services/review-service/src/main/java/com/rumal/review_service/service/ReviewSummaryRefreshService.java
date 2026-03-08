package com.rumal.review_service.service;

import com.rumal.review_service.entity.ReviewProductSummary;
import com.rumal.review_service.entity.ReviewSummaryRefreshTask;
import com.rumal.review_service.entity.ReviewSummaryRefreshTaskStatus;
import com.rumal.review_service.repository.ReviewProductSummaryRepository;
import com.rumal.review_service.repository.ReviewRepository;
import com.rumal.review_service.repository.ReviewSummaryRefreshTaskRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewSummaryRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryRefreshService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewProductSummaryRepository reviewProductSummaryRepository;
    private final ReviewSummaryRefreshTaskRepository reviewSummaryRefreshTaskRepository;
    private final ReviewCacheVersionService reviewCacheVersionService;
    private final PlatformTransactionManager transactionManager;

    @Value("${review.summary.refresh.batch-size:50}")
    private int batchSize;

    @Value("${review.summary.refresh.failure-backoff:PT30S}")
    private Duration failureBackoff;

    public Optional<ReviewProductSummary> getSummary(UUID productId) {
        return reviewProductSummaryRepository.findById(productId);
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void requestRefresh(UUID productId) {
        Instant now = Instant.now();
        ReviewSummaryRefreshTask task = reviewSummaryRefreshTaskRepository.findByProductId(productId)
                .orElseGet(() -> {
                    ReviewSummaryRefreshTask fresh = new ReviewSummaryRefreshTask();
                    fresh.setProductId(productId);
                    return fresh;
                });
        task.setStatus(ReviewSummaryRefreshTaskStatus.PENDING);
        task.setNextAttemptAt(now);
        task.setProcessingStartedAt(null);
        task.setLastError(null);
        try {
            reviewSummaryRefreshTaskRepository.save(task);
        } catch (DataIntegrityViolationException ex) {
            reviewSummaryRefreshTaskRepository.findByProductId(productId).ifPresent(existing -> {
                existing.setStatus(ReviewSummaryRefreshTaskStatus.PENDING);
                existing.setNextAttemptAt(now);
                existing.setProcessingStartedAt(null);
                existing.setLastError(null);
                reviewSummaryRefreshTaskRepository.save(existing);
            });
        }
    }

    @Scheduled(
            initialDelayString = "${review.summary.refresh.initial-delay:PT20S}",
            fixedDelayString = "${review.summary.refresh.fixed-delay:PT5S}"
    )
    public void processPendingRefreshes() {
        Instant now = Instant.now();
        List<UUID> taskIds = reviewSummaryRefreshTaskRepository.findDueTaskIds(
                Set.of(ReviewSummaryRefreshTaskStatus.PENDING, ReviewSummaryRefreshTaskStatus.FAILED),
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        for (UUID taskId : taskIds) {
            processTask(taskId, now);
        }
    }

    @Scheduled(
            initialDelayString = "${review.summary.refresh.backfill-initial-delay:PT30S}",
            fixedDelayString = "${review.summary.refresh.backfill-interval:PT6H}"
    )
    public void enqueueMissingProductSummaries() {
        for (UUID productId : reviewRepository.findDistinctProductIds()) {
            if (productId != null) {
                requestRefresh(productId);
            }
        }
    }

    private void processTask(UUID taskId, Instant claimTime) {
        ReviewSummaryRefreshTask task = claimTask(taskId, claimTime);
        if (task == null) {
            return;
        }

        try {
            ReviewProductSummary summary = recomputeSummary(task.getProductId());
            completeTask(taskId, summary);
        } catch (RuntimeException ex) {
            failTask(taskId, ex);
        }
    }

    private ReviewProductSummary recomputeSummary(UUID productId) {
        double average = reviewRepository.averageRatingByProductId(productId);
        long total = reviewRepository.countActiveByProductId(productId);
        List<Object[]> distribution = reviewRepository.countByProductIdGroupByRating(productId);

        ReviewProductSummary summary = reviewProductSummaryRepository.findById(productId)
                .orElseGet(ReviewProductSummary::new);
        summary.setProductId(productId);
        summary.setAverageRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        summary.setTotalReviews(total);
        summary.setRating1Count(0L);
        summary.setRating2Count(0L);
        summary.setRating3Count(0L);
        summary.setRating4Count(0L);
        summary.setRating5Count(0L);
        for (Object[] row : distribution) {
            int rating = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            switch (rating) {
                case 1 -> summary.setRating1Count(count);
                case 2 -> summary.setRating2Count(count);
                case 3 -> summary.setRating3Count(count);
                case 4 -> summary.setRating4Count(count);
                case 5 -> summary.setRating5Count(count);
                default -> log.warn("Ignoring out-of-range rating={} for productId={}", rating, productId);
            }
        }
        return summary;
    }

    private ReviewSummaryRefreshTask claimTask(UUID taskId, Instant claimTime) {
        return newTransactionTemplate(10).execute(status -> {
            if (reviewSummaryRefreshTaskRepository.claimTask(taskId, claimTime) == 0) {
                return null;
            }
            return reviewSummaryRefreshTaskRepository.findById(taskId).orElse(null);
        });
    }

    private void completeTask(UUID taskId, ReviewProductSummary summary) {
        newTransactionTemplate(15).executeWithoutResult(status -> {
            ReviewSummaryRefreshTask task = reviewSummaryRefreshTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            reviewProductSummaryRepository.save(summary);
            task.setStatus(ReviewSummaryRefreshTaskStatus.COMPLETED);
            task.setAttemptCount(0);
            task.setProcessingStartedAt(null);
            task.setNextAttemptAt(Instant.now());
            task.setLastError(null);
            reviewSummaryRefreshTaskRepository.save(task);
            reviewCacheVersionService.bumpReviewSummaryCache();
        });
    }

    private void failTask(UUID taskId, RuntimeException ex) {
        newTransactionTemplate(15).executeWithoutResult(status -> {
            ReviewSummaryRefreshTask task = reviewSummaryRefreshTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            int attempts = task.getAttemptCount() + 1;
            task.setAttemptCount(attempts);
            task.setStatus(ReviewSummaryRefreshTaskStatus.FAILED);
            task.setProcessingStartedAt(null);
            task.setNextAttemptAt(Instant.now().plus(failureBackoff.multipliedBy(Math.min(attempts, 4L))));
            task.setLastError(truncate(ex.getMessage()));
            reviewSummaryRefreshTaskRepository.save(task);
            log.warn("Failed to refresh review summary for productId={} taskId={}", task.getProductId(), task.getId(), ex);
        });
    }

    private TransactionTemplate newTransactionTemplate(int timeoutSeconds) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(timeoutSeconds);
        return template;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
