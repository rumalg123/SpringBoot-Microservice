package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.analytics.*;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class InternalPromotionAnalyticsService {

    private final PromotionCampaignRepository promotionCampaignRepository;

    public PromotionPlatformSummary getPlatformSummary() {
        long total = promotionCampaignRepository.count();
        long active = promotionCampaignRepository.countByLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        long draft = promotionCampaignRepository.countByLifecycleStatus(PromotionLifecycleStatus.DRAFT);
        long archived = promotionCampaignRepository.countByLifecycleStatus(PromotionLifecycleStatus.ARCHIVED);
        long flashSales = promotionCampaignRepository.countActiveFlashSales();
        BigDecimal totalBudget = promotionCampaignRepository.sumTotalBudget();
        BigDecimal totalBurned = promotionCampaignRepository.sumTotalBurnedBudget();

        double utilization = totalBudget.compareTo(BigDecimal.ZERO) > 0
            ? totalBurned.divide(totalBudget, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
            : 0.0;

        return new PromotionPlatformSummary(total, active, draft, archived,
            flashSales, totalBudget, totalBurned,
            Math.round(utilization * 100.0) / 100.0);
    }

    public List<PromotionRoiEntry> getPromotionRoi(int limit) {
        return promotionCampaignRepository.findTopByBudgetUtilization(PageRequest.of(0, limit)).stream()
            .map(pc -> {
                double util = pc.getBudgetAmount() != null && pc.getBudgetAmount().compareTo(BigDecimal.ZERO) > 0
                    ? (pc.getBurnedBudgetAmount() != null ? pc.getBurnedBudgetAmount() : BigDecimal.ZERO)
                        .divide(pc.getBudgetAmount(), 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;
                return new PromotionRoiEntry(
                    pc.getId(), pc.getName(), pc.getVendorId(),
                    pc.getBudgetAmount(),
                    pc.getBurnedBudgetAmount() != null ? pc.getBurnedBudgetAmount() : BigDecimal.ZERO,
                    Math.round(util * 100.0) / 100.0,
                    pc.getBenefitType().name(),
                    pc.isFlashSale());
            })
            .toList();
    }

    public VendorPromotionSummary getVendorSummary(UUID vendorId) {
        long total = promotionCampaignRepository.countByVendorId(vendorId);
        long active = promotionCampaignRepository.countByVendorIdAndLifecycleStatus(vendorId, PromotionLifecycleStatus.ACTIVE);
        BigDecimal budget = promotionCampaignRepository.sumBudgetByVendorId(vendorId);
        BigDecimal burned = promotionCampaignRepository.sumBurnedBudgetByVendorId(vendorId);

        return new VendorPromotionSummary(vendorId, total, active, budget, burned);
    }
}
