package com.rumal.payment_service.repo;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    Optional<Payment> findByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Page<Payment> findByCustomerKeycloakId(String customerKeycloakId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE (:customerId IS NULL OR p.customerId = :customerId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Payment> findFiltered(UUID customerId, PaymentStatus status, Pageable pageable);

    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now);
}
