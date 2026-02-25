package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.ReservationStatus;
import com.rumal.inventory_service.entity.StockReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    List<StockReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @Query("select r from StockReservation r join fetch r.stockItem where r.orderId = :orderId and r.status = :status")
    List<StockReservation> findByOrderIdAndStatusWithStockItem(
            @Param("orderId") UUID orderId,
            @Param("status") ReservationStatus status
    );

    @Query(value = "select r from StockReservation r join fetch r.stockItem where r.status = :status and r.expiresAt <= :now",
            countQuery = "select count(r) from StockReservation r where r.status = :status and r.expiresAt <= :now")
    Page<StockReservation> findExpiredReservations(
            @Param("status") ReservationStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    Page<StockReservation> findByStatus(ReservationStatus status, Pageable pageable);

    @Query("""
            select r from StockReservation r join fetch r.stockItem
            where (:status is null or r.status = :status)
              and (:orderId is null or r.orderId = :orderId)
            """)
    Page<StockReservation> findFiltered(
            @Param("status") ReservationStatus status,
            @Param("orderId") UUID orderId,
            Pageable pageable
    );
}
