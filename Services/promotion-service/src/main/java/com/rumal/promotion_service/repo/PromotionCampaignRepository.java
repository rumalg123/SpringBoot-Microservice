package com.rumal.promotion_service.repo;

import com.rumal.promotion_service.entity.PromotionCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, UUID>, JpaSpecificationExecutor<PromotionCampaign> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionCampaign p where p.id = :id")
    Optional<PromotionCampaign> findByIdForUpdate(@Param("id") UUID id);
}
