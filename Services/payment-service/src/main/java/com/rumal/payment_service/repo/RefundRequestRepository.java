package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.RefundRequest;
import com.rumal.payment_service.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(UUID id);

    Optional<RefundRequest> findByVendorOrderIdAndStatusNotIn(UUID vendorOrderId, List<RefundStatus> terminalStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.vendorOrderId = :vendorOrderId AND r.status NOT IN :terminalStatuses")
    Optional<RefundRequest> findByVendorOrderIdAndStatusNotInForUpdate(@Param("vendorOrderId") UUID vendorOrderId, @Param("terminalStatuses") List<RefundStatus> terminalStatuses);

    @EntityGraph(attributePaths = "payment")
    Page<RefundRequest> findByCustomerKeycloakId(String customerKeycloakId, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    @Query("SELECT r FROM RefundRequest r WHERE r.vendorId = :vendorId " +
            "AND (:status IS NULL OR r.status = :status)")
    Page<RefundRequest> findByVendorFiltered(UUID vendorId, RefundStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    @Query("SELECT r FROM RefundRequest r WHERE (:vendorId IS NULL OR r.vendorId = :vendorId) " +
            "AND (:status IS NULL OR r.status = :status)")
    Page<RefundRequest> findAllFiltered(UUID vendorId, RefundStatus status, Pageable pageable);

    Page<RefundRequest> findByStatusAndVendorResponseDeadlineBefore(RefundStatus status, Instant deadline, Pageable pageable);

    // C-03: Sum of refund amounts for a vendor order that are in active/completed states
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM RefundRequest r " +
            "WHERE r.vendorOrderId = :vendorOrderId " +
            "AND r.status NOT IN (com.rumal.payment_service.entity.RefundStatus.ADMIN_REJECTED, " +
            "com.rumal.payment_service.entity.RefundStatus.REFUND_FAILED)")
    BigDecimal sumRefundedAmountByVendorOrderId(@Param("vendorOrderId") UUID vendorOrderId);

    // H-14: Sum of all active refund amounts across ALL vendor orders for a given payment
    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM RefundRequest r " +
            "WHERE r.payment.id = :paymentId " +
            "AND r.status NOT IN (com.rumal.payment_service.entity.RefundStatus.ADMIN_REJECTED, " +
            "com.rumal.payment_service.entity.RefundStatus.REFUND_FAILED)")
    BigDecimal sumActiveRefundsByPaymentId(@Param("paymentId") UUID paymentId);
}
