package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OrderExportJob;
import com.rumal.order_service.entity.OrderExportJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderExportJobRepository extends JpaRepository<OrderExportJob, UUID> {

    @Query(value = """
            SELECT * FROM order_export_jobs
            WHERE (
                    status = 'PENDING'
                 OR (status = 'PROCESSING' AND processing_lease_until IS NOT NULL AND processing_lease_until <= :now)
                  )
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderExportJob> findJobsReadyToClaim(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM OrderExportJob j WHERE j.id = :id")
    Optional<OrderExportJob> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT j FROM OrderExportJob j WHERE j.status IN :statuses AND j.expiresAt IS NOT NULL AND j.expiresAt <= :now")
    List<OrderExportJob> findExpiredJobs(
            @Param("statuses") Collection<OrderExportJobStatus> statuses,
            @Param("now") Instant now
    );
}
