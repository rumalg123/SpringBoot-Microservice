package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.PublicPromotionResponse;
import com.rumal.promotion_service.dto.PromotionSpendTierResponse;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.entity.PromotionSpendTier;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PublicPromotionService {

    private final PromotionCampaignRepository promotionCampaignRepository;

    @Cacheable(cacheNames = "publicPromotionList", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #q + '-' + #scopeType + '-' + #benefitType")
    public Page<PublicPromotionResponse> list(
            Pageable pageable,
            String q,
            PromotionScopeType scopeType,
            PromotionBenefitType benefitType
    ) {
        Instant now = Instant.now();
        Specification<PromotionCampaign> spec = activeAndApprovedSpec(now);

        if (StringUtils.hasText(q)) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        if (scopeType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("scopeType"), scopeType));
        }
        if (benefitType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("benefitType"), benefitType));
        }

        return promotionCampaignRepository.findAll(spec, pageable).map(this::toPublicResponse);
    }

    public Page<PublicPromotionResponse> listActiveFlashSales(Pageable pageable) {
        Instant now = Instant.now();
        Specification<PromotionCampaign> spec = activeAndApprovedSpec(now)
                .and((root, query, cb) -> cb.isTrue(root.get("flashSale")))
                .and((root, query, cb) -> cb.or(
                        cb.isNull(root.get("flashSaleStartAt")),
                        cb.lessThanOrEqualTo(root.get("flashSaleStartAt"), now)
                ))
                .and((root, query, cb) -> cb.or(
                        cb.isNull(root.get("flashSaleEndAt")),
                        cb.greaterThanOrEqualTo(root.get("flashSaleEndAt"), now)
                ));

        return promotionCampaignRepository.findAll(spec, pageable).map(this::toPublicResponse);
    }

    @Cacheable(cacheNames = "publicPromotionById", key = "#id")
    public PublicPromotionResponse get(UUID id) {
        Instant now = Instant.now();
        Specification<PromotionCampaign> spec = activeAndApprovedSpec(now)
                .and((root, query, cb) -> cb.equal(root.get("id"), id));

        return promotionCampaignRepository.findOne(spec)
                .map(this::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
    }

    private Specification<PromotionCampaign> activeAndApprovedSpec(Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lifecycleStatus"), PromotionLifecycleStatus.ACTIVE));
            predicates.add(root.get("approvalStatus").in(PromotionApprovalStatus.APPROVED, PromotionApprovalStatus.NOT_REQUIRED));
            predicates.add(cb.or(cb.isNull(root.get("startsAt")), cb.lessThanOrEqualTo(root.get("startsAt"), now)));
            predicates.add(cb.or(cb.isNull(root.get("endsAt")), cb.greaterThanOrEqualTo(root.get("endsAt"), now)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PublicPromotionResponse toPublicResponse(PromotionCampaign entity) {
        return new PublicPromotionResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getScopeType(),
                entity.getApplicationLevel(),
                entity.getBenefitType(),
                entity.getBenefitValue(),
                entity.getBuyQuantity(),
                entity.getGetQuantity(),
                entity.getSpendTiers() == null ? List.of() : entity.getSpendTiers().stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(PromotionSpendTier::getThresholdAmount, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(tier -> new PromotionSpendTierResponse(
                                normalizeNullableMoney(tier.getThresholdAmount()),
                                normalizeNullableMoney(tier.getDiscountAmount())
                        ))
                        .toList(),
                entity.getMinimumOrderAmount(),
                entity.getMaximumDiscountAmount(),
                entity.isStackable(),
                entity.getStackingGroup(),
                entity.isAutoApply(),
                entity.getTargetProductIds() == null ? Set.of() : Set.copyOf(entity.getTargetProductIds()),
                entity.getTargetCategoryIds() == null ? Set.of() : Set.copyOf(entity.getTargetCategoryIds()),
                entity.isFlashSale(),
                entity.getFlashSaleStartAt(),
                entity.getFlashSaleEndAt(),
                entity.getStartsAt(),
                entity.getEndsAt()
        );
    }

    private BigDecimal normalizeNullableMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
