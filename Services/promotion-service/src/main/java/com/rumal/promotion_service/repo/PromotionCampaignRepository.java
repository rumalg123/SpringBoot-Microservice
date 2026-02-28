package com.rumal.promotion_service.repo;

import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, UUID>, JpaSpecificationExecutor<PromotionCampaign> {

    Page<PromotionCampaign> findByLifecycleStatusAndApprovalStatusIn(
            PromotionLifecycleStatus lifecycleStatus,
            Collection<PromotionApprovalStatus> approvalStatuses,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM PromotionCampaign p
            WHERE p.lifecycleStatus = :status
              AND p.approvalStatus IN :approvalStatuses
              AND (p.scopeType = 'ORDER'
                   OR (p.scopeType = 'VENDOR' AND p.vendorId IN :vendorIds)
                   OR p.scopeType IN ('PRODUCT', 'CATEGORY'))
            """)
    Page<PromotionCampaign> findActiveByScope(
            @Param("status") PromotionLifecycleStatus status,
            @Param("approvalStatuses") Collection<PromotionApprovalStatus> approvalStatuses,
            @Param("vendorIds") Collection<UUID> vendorIds,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionCampaign p where p.id = :id")
    Optional<PromotionCampaign> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PromotionCampaign p SET p.flashSaleRedemptionCount = p.flashSaleRedemptionCount + 1 WHERE p.id = :id AND p.flashSaleRedemptionCount < p.flashSaleMaxRedemptions")
    int incrementFlashSaleRedemptionCount(@Param("id") UUID id);

    // --- Analytics queries ---

    long countByLifecycleStatus(PromotionLifecycleStatus status);

    @Query("SELECT COUNT(pc) FROM PromotionCampaign pc WHERE pc.flashSale = true AND pc.lifecycleStatus = com.rumal.promotion_service.entity.PromotionLifecycleStatus.ACTIVE")
    long countActiveFlashSales();

    @Query("SELECT COALESCE(SUM(pc.budgetAmount), 0) FROM PromotionCampaign pc WHERE pc.budgetAmount IS NOT NULL")
    java.math.BigDecimal sumTotalBudget();

    @Query("SELECT COALESCE(SUM(pc.burnedBudgetAmount), 0) FROM PromotionCampaign pc WHERE pc.burnedBudgetAmount IS NOT NULL")
    java.math.BigDecimal sumTotalBurnedBudget();

    @Query("SELECT pc FROM PromotionCampaign pc WHERE pc.budgetAmount IS NOT NULL AND pc.budgetAmount > 0 ORDER BY pc.burnedBudgetAmount DESC NULLS LAST")
    List<PromotionCampaign> findTopByBudgetUtilization(org.springframework.data.domain.Pageable pageable);

    // Vendor-specific
    @Query("SELECT COUNT(pc) FROM PromotionCampaign pc WHERE pc.vendorId = :vendorId")
    long countByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT COUNT(pc) FROM PromotionCampaign pc WHERE pc.vendorId = :vendorId AND pc.lifecycleStatus = :status")
    long countByVendorIdAndLifecycleStatus(@Param("vendorId") UUID vendorId, @Param("status") PromotionLifecycleStatus status);

    @Query("SELECT COALESCE(SUM(pc.budgetAmount), 0) FROM PromotionCampaign pc WHERE pc.vendorId = :vendorId AND pc.budgetAmount IS NOT NULL")
    java.math.BigDecimal sumBudgetByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT COALESCE(SUM(pc.burnedBudgetAmount), 0) FROM PromotionCampaign pc WHERE pc.vendorId = :vendorId AND pc.burnedBudgetAmount IS NOT NULL")
    java.math.BigDecimal sumBurnedBudgetByVendorId(@Param("vendorId") UUID vendorId);
}
