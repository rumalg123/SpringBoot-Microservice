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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, UUID>, JpaSpecificationExecutor<PromotionCampaign> {

    List<PromotionCampaign> findByLifecycleStatusAndApprovalStatusIn(
            PromotionLifecycleStatus lifecycleStatus,
            Collection<PromotionApprovalStatus> approvalStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionCampaign p where p.id = :id")
    Optional<PromotionCampaign> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PromotionCampaign p SET p.flashSaleRedemptionCount = p.flashSaleRedemptionCount + 1 WHERE p.id = :id AND p.flashSaleRedemptionCount < p.flashSaleMaxRedemptions")
    int incrementFlashSaleRedemptionCount(@Param("id") UUID id);
}
