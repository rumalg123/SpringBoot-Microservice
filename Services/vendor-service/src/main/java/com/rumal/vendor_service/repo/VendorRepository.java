package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vendor v WHERE v.id = :id")
    Optional<Vendor> findByIdForUpdate(@Param("id") UUID id);

    Optional<Vendor> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    Page<Vendor> findByDeletedFalseOrderByNameAsc(Pageable pageable);
    Page<Vendor> findByDeletedTrueOrderByUpdatedAtDesc(Pageable pageable);
    Page<Vendor> findByDeletedFalseAndActiveTrueAndStatusOrderByNameAsc(VendorStatus status, Pageable pageable);
    Page<Vendor> findByDeletedFalseAndActiveTrueAndAcceptingOrdersTrueAndStatusOrderByNameAsc(VendorStatus status, Pageable pageable);

    @Query(value = "SELECT DISTINCT v FROM Vendor v LEFT JOIN v.specializations s " +
           "WHERE v.deleted = false AND v.active = true AND v.status = :status " +
           "AND (v.primaryCategory = :category OR s = :category)",
           countQuery = "SELECT COUNT(DISTINCT v) FROM Vendor v LEFT JOIN v.specializations s " +
           "WHERE v.deleted = false AND v.active = true AND v.status = :status " +
           "AND (v.primaryCategory = :category OR s = :category)")
    Page<Vendor> findActiveVendorsByCategory(@Param("status") VendorStatus status, @Param("category") String category, Pageable pageable);

    @Query(value = "SELECT DISTINCT v FROM Vendor v LEFT JOIN v.specializations s " +
           "WHERE v.deleted = false AND v.active = true AND v.acceptingOrders = true AND v.status = :status " +
           "AND (v.primaryCategory = :category OR s = :category)",
           countQuery = "SELECT COUNT(DISTINCT v) FROM Vendor v LEFT JOIN v.specializations s " +
           "WHERE v.deleted = false AND v.active = true AND v.acceptingOrders = true AND v.status = :status " +
           "AND (v.primaryCategory = :category OR s = :category)")
    Page<Vendor> findActiveAcceptingVendorsByCategory(@Param("status") VendorStatus status, @Param("category") String category, Pageable pageable);

    // --- Analytics queries ---

    long countByDeletedFalse();

    long countByDeletedFalseAndStatus(VendorStatus status);

    long countByDeletedFalseAndVerifiedTrue();

    @Query("SELECT COALESCE(AVG(v.commissionRate), 0) FROM Vendor v WHERE v.deleted = false AND v.active = true")
    java.math.BigDecimal avgCommissionRate();

    @Query("SELECT COALESCE(AVG(v.fulfillmentRate), 0) FROM Vendor v WHERE v.deleted = false AND v.active = true AND v.fulfillmentRate IS NOT NULL")
    java.math.BigDecimal avgFulfillmentRate();

    @Query("SELECT v FROM Vendor v WHERE v.deleted = false AND v.active = true ORDER BY v.totalOrdersCompleted DESC")
    List<Vendor> findTopVendorsByOrdersCompleted(Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE v.deleted = false AND v.active = true ORDER BY v.averageRating DESC")
    List<Vendor> findTopVendorsByRating(Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE v.deleted = false AND v.active = true ORDER BY v.fulfillmentRate DESC NULLS LAST")
    List<Vendor> findTopVendorsByFulfillment(Pageable pageable);
}
