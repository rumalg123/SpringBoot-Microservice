package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    Optional<Payment> findByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status IN :statuses")
    Optional<Payment> findByOrderIdAndStatusInForUpdate(@Param("orderId") UUID orderId, @Param("statuses") List<PaymentStatus> statuses);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Page<Payment> findByCustomerKeycloakId(String customerKeycloakId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE (:customerId IS NULL OR p.customerId = :customerId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Payment> findFiltered(UUID customerId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.orderSyncPending = true AND p.status IN :statuses")
    Page<Payment> findOrderSyncPending(@Param("statuses") List<PaymentStatus> statuses, Pageable pageable);

    // --- Analytics queries ---

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(AVG(p.amount), 0) FROM Payment p WHERE p.status = com.rumal.payment_service.entity.PaymentStatus.SUCCESS")
    BigDecimal avgSuccessfulPaymentAmount();

    @Query("SELECT p.paymentMethod, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = com.rumal.payment_service.entity.PaymentStatus.SUCCESS AND p.paymentMethod IS NOT NULL GROUP BY p.paymentMethod ORDER BY SUM(p.amount) DESC")
    List<Object[]> getPaymentMethodBreakdown();
}
