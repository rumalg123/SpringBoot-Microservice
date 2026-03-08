package com.rumal.review_service.repository;

import com.rumal.review_service.entity.ReviewSummaryRefreshTask;
import com.rumal.review_service.entity.ReviewSummaryRefreshTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSummaryRefreshTaskRepository extends JpaRepository<ReviewSummaryRefreshTask, UUID> {

    Optional<ReviewSummaryRefreshTask> findByProductId(UUID productId);

    @Query("""
            SELECT t.id
            FROM ReviewSummaryRefreshTask t
            WHERE t.status IN :statuses
              AND t.nextAttemptAt <= :now
            ORDER BY t.updatedAt ASC
            """)
    List<UUID> findDueTaskIds(
            @Param("statuses") Collection<ReviewSummaryRefreshTaskStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE ReviewSummaryRefreshTask t
            SET t.status = com.rumal.review_service.entity.ReviewSummaryRefreshTaskStatus.PROCESSING,
                t.processingStartedAt = :now,
                t.lastError = NULL
            WHERE t.id = :id
              AND t.status IN (
                    com.rumal.review_service.entity.ReviewSummaryRefreshTaskStatus.PENDING,
                    com.rumal.review_service.entity.ReviewSummaryRefreshTaskStatus.FAILED
              )
              AND t.nextAttemptAt <= :now
            """)
    int claimTask(@Param("id") UUID id, @Param("now") Instant now);
}
