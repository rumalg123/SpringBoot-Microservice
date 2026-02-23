package com.rumal.promotion_service.repo;

import com.rumal.promotion_service.entity.PromotionCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, UUID>, JpaSpecificationExecutor<PromotionCampaign> {
}
