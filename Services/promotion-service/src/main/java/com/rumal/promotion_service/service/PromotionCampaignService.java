package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.PromotionApprovalDecisionRequest;
import com.rumal.promotion_service.dto.PromotionResponse;
import com.rumal.promotion_service.dto.PromotionSpendTierResponse;
import com.rumal.promotion_service.dto.UpsertPromotionRequest;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.entity.PromotionSpendTier;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PromotionCampaignService {

    private final PromotionCampaignRepository promotionCampaignRepository;

    @Cacheable(cacheNames = "promotionAdminList",
            key = "'list-P' + #pageable.pageNumber + '-S' + #pageable.pageSize + '-SO' + #pageable.sort"
                    + " + '-q' + (#q == null ? '' : #q)"
                    + " + '-v' + (#vendorId == null ? '' : #vendorId)"
                    + " + '-ls' + (#lifecycleStatus == null ? '' : #lifecycleStatus.name())"
                    + " + '-as' + (#approvalStatus == null ? '' : #approvalStatus.name())"
                    + " + '-sc' + (#scopeType == null ? '' : #scopeType.name())"
                    + " + '-bt' + (#benefitType == null ? '' : #benefitType.name())")
    @Transactional(readOnly = true)
    public Page<PromotionResponse> list(
            Pageable pageable,
            String q,
            UUID vendorId,
            PromotionLifecycleStatus lifecycleStatus,
            PromotionApprovalStatus approvalStatus,
            PromotionScopeType scopeType,
            PromotionBenefitType benefitType
    ) {
        Specification<PromotionCampaign> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(q)) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (lifecycleStatus != null) {
                predicates.add(cb.equal(root.get("lifecycleStatus"), lifecycleStatus));
            }
            if (approvalStatus != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), approvalStatus));
            }
            if (scopeType != null) {
                predicates.add(cb.equal(root.get("scopeType"), scopeType));
            }
            if (benefitType != null) {
                predicates.add(cb.equal(root.get("benefitType"), benefitType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return promotionCampaignRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Cacheable(cacheNames = "promotionById", key = "#id")
    @Transactional(readOnly = true)
    public PromotionResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true)
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse create(
            UpsertPromotionRequest request,
            String actorUserSub,
            AdminPromotionAccessScopeService.AdminActorScope actorScope
    ) {
        validateRequest(request, actorScope, null);
        PromotionCampaign campaign = new PromotionCampaign();
        applyRequest(campaign, request);
        campaign.setLifecycleStatus(PromotionLifecycleStatus.DRAFT);
        if (actorScope.isPlatformPrivileged()) {
            campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
            campaign.setApprovedAt(Instant.now());
            campaign.setApprovedByUserSub(actorUserSub);
            campaign.setApprovalNote("Platform-created promotion");
        } else {
            campaign.setApprovalStatus(PromotionApprovalStatus.PENDING);
            campaign.setSubmittedAt(Instant.now());
            campaign.setSubmittedByUserSub(actorUserSub);
            campaign.setApprovalNote("Awaiting platform approval");
        }
        campaign.setCreatedByUserSub(trimToNull(actorUserSub));
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse update(
            UUID id,
            UpsertPromotionRequest request,
            String actorUserSub,
            AdminPromotionAccessScopeService.AdminActorScope actorScope
    ) {
        PromotionCampaign campaign = getEntity(id);
        validateRequest(request, actorScope, campaign);
        applyRequest(campaign, request);
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        if (!actorScope.isPlatformPrivileged()) {
            campaign.setApprovalStatus(PromotionApprovalStatus.PENDING);
            campaign.setSubmittedAt(Instant.now());
            campaign.setSubmittedByUserSub(trimToNull(actorUserSub));
            campaign.setApprovalNote("Updated by vendor; pending platform re-approval");
            if (campaign.getLifecycleStatus() == PromotionLifecycleStatus.ACTIVE) {
                campaign.setLifecycleStatus(PromotionLifecycleStatus.PAUSED);
            }
        }
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse submitForApproval(UUID id, String actorUserSub) {
        PromotionCampaign campaign = getEntity(id);
        if (campaign.getVendorId() == null) {
            throw new ValidationException("Platform-wide promotion does not require vendor submission");
        }
        if (campaign.getApprovalStatus() == PromotionApprovalStatus.APPROVED) {
            throw new ValidationException("Promotion is already approved");
        }
        campaign.setApprovalStatus(PromotionApprovalStatus.PENDING);
        campaign.setSubmittedAt(Instant.now());
        campaign.setSubmittedByUserSub(trimToNull(actorUserSub));
        campaign.setApprovalNote("Submitted for platform approval");
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse approve(UUID id, String actorUserSub, PromotionApprovalDecisionRequest request) {
        PromotionCampaign campaign = getEntity(id);
        if (campaign.getApprovalStatus() == PromotionApprovalStatus.NOT_REQUIRED) {
            throw new ValidationException("Platform-created promotion does not require approval");
        }
        if (campaign.getApprovalStatus() == PromotionApprovalStatus.APPROVED) {
            throw new ValidationException("Promotion is already approved");
        }
        campaign.setApprovalStatus(PromotionApprovalStatus.APPROVED);
        campaign.setApprovedAt(Instant.now());
        campaign.setApprovedByUserSub(trimToNull(actorUserSub));
        campaign.setRejectedAt(null);
        campaign.setRejectedByUserSub(null);
        campaign.setApprovalNote(trimToNull(request == null ? null : request.note()));
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse reject(UUID id, String actorUserSub, PromotionApprovalDecisionRequest request) {
        PromotionCampaign campaign = getEntity(id);
        if (campaign.getApprovalStatus() == PromotionApprovalStatus.NOT_REQUIRED) {
            throw new ValidationException("Platform-created promotion does not require approval");
        }
        campaign.setApprovalStatus(PromotionApprovalStatus.REJECTED);
        campaign.setRejectedAt(Instant.now());
        campaign.setRejectedByUserSub(trimToNull(actorUserSub));
        campaign.setApprovalNote(trimToNull(request == null ? null : request.note()));
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        if (campaign.getLifecycleStatus() == PromotionLifecycleStatus.ACTIVE) {
            campaign.setLifecycleStatus(PromotionLifecycleStatus.PAUSED);
        }
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse activate(UUID id, String actorUserSub) {
        PromotionCampaign campaign = getEntity(id);
        if (campaign.getLifecycleStatus() == PromotionLifecycleStatus.ARCHIVED) {
            throw new ValidationException("Archived promotion cannot be activated");
        }
        if (requiresApproval(campaign) && campaign.getApprovalStatus() != PromotionApprovalStatus.APPROVED) {
            throw new ValidationException("Promotion must be approved before activation");
        }
        if (campaign.getEndsAt() != null && campaign.getEndsAt().isBefore(Instant.now())) {
            throw new ValidationException("Promotion end time is already in the past");
        }
        campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse pause(UUID id, String actorUserSub) {
        PromotionCampaign campaign = getEntity(id);
        if (campaign.getLifecycleStatus() == PromotionLifecycleStatus.ARCHIVED) {
            throw new ValidationException("Archived promotion cannot be paused");
        }
        campaign.setLifecycleStatus(PromotionLifecycleStatus.PAUSED);
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "promotionById", key = "#id"),
            @CacheEvict(cacheNames = "promotionAdminList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionList", allEntries = true),
            @CacheEvict(cacheNames = "publicPromotionById", key = "#id")
    })
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PromotionResponse archive(UUID id, String actorUserSub) {
        PromotionCampaign campaign = getEntity(id);
        campaign.setLifecycleStatus(PromotionLifecycleStatus.ARCHIVED);
        campaign.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(promotionCampaignRepository.save(campaign));
    }

    private PromotionCampaign getEntity(UUID id) {
        return promotionCampaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
    }

    private void validateRequest(
            UpsertPromotionRequest request,
            AdminPromotionAccessScopeService.AdminActorScope actorScope,
            PromotionCampaign existing
    ) {
        Objects.requireNonNull(request, "request is required");

        if (request.benefitType() == PromotionBenefitType.FREE_SHIPPING) {
            if (request.applicationLevel() != com.rumal.promotion_service.entity.PromotionApplicationLevel.SHIPPING) {
                throw new ValidationException("FREE_SHIPPING promotions must use applicationLevel=SHIPPING");
            }
        } else if (request.benefitType() == PromotionBenefitType.BUY_X_GET_Y) {
            if (request.applicationLevel() != com.rumal.promotion_service.entity.PromotionApplicationLevel.LINE_ITEM) {
                throw new ValidationException("BUY_X_GET_Y promotions must use applicationLevel=LINE_ITEM");
            }
            if (request.buyQuantity() == null || request.buyQuantity() < 1
                    || request.getQuantity() == null || request.getQuantity() < 1) {
                throw new ValidationException("BUY_X_GET_Y promotions require buyQuantity and getQuantity");
            }
            if (request.benefitValue() != null && request.benefitValue().compareTo(BigDecimal.ZERO) > 0) {
                throw new ValidationException("benefitValue is not used for BUY_X_GET_Y promotions");
            }
        } else if (request.benefitType() == PromotionBenefitType.TIERED_SPEND) {
            if (request.applicationLevel() != com.rumal.promotion_service.entity.PromotionApplicationLevel.CART) {
                throw new ValidationException("TIERED_SPEND promotions must use applicationLevel=CART");
            }
            if (!request.spendTiersOrEmpty().stream().anyMatch(Objects::nonNull)) {
                throw new ValidationException("TIERED_SPEND promotions require spendTiers");
            }
            Set<BigDecimal> thresholds = new HashSet<>();
            for (var tier : request.spendTiersOrEmpty()) {
                if (tier == null) {
                    continue;
                }
                BigDecimal threshold = normalizeNullableMoney(tier.thresholdAmount());
                BigDecimal discount = normalizeNullableMoney(tier.discountAmount());
                if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException("Tier thresholdAmount must be greater than 0");
                }
                if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException("Tier discountAmount must be greater than 0");
                }
                if (!thresholds.add(threshold)) {
                    throw new ValidationException("Tier thresholdAmount values must be unique within a promotion");
                }
            }
            if (request.benefitValue() != null && request.benefitValue().compareTo(BigDecimal.ZERO) > 0) {
                throw new ValidationException("benefitValue is not used for TIERED_SPEND promotions");
            }
        } else if (request.benefitType() == PromotionBenefitType.BUNDLE_DISCOUNT) {
            if (request.applicationLevel() != com.rumal.promotion_service.entity.PromotionApplicationLevel.CART) {
                throw new ValidationException("BUNDLE_DISCOUNT promotions must use applicationLevel=CART");
            }
            if (request.scopeType() != PromotionScopeType.PRODUCT) {
                throw new ValidationException("BUNDLE_DISCOUNT promotions must use scopeType=PRODUCT");
            }
            if (request.targetProductIdsOrEmpty().size() < 2) {
                throw new ValidationException("BUNDLE_DISCOUNT promotions require at least 2 targetProductIds");
            }
            if (request.benefitValue() == null || request.benefitValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("BUNDLE_DISCOUNT promotions require benefitValue > 0");
            }
        } else if (request.benefitType() == PromotionBenefitType.PERCENTAGE_OFF) {
            if (request.benefitValue() == null || request.benefitValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("PERCENTAGE_OFF benefitValue must be greater than 0");
            }
            if (request.benefitValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ValidationException("PERCENTAGE_OFF benefitValue must not exceed 100");
            }
        } else if (request.benefitType() == PromotionBenefitType.FIXED_AMOUNT_OFF) {
            if (request.benefitValue() == null || request.benefitValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("FIXED_AMOUNT_OFF benefitValue must be greater than 0");
            }
        } else {
            if (request.benefitValue() == null || request.benefitValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("benefitValue must be greater than 0 for non-shipping benefits");
            }
        }
        if (request.budgetAmount() != null && request.budgetAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("budgetAmount cannot be negative");
        }

        if (request.scopeType() == PromotionScopeType.PRODUCT && request.targetProductIdsOrEmpty().isEmpty()) {
            throw new ValidationException("targetProductIds is required for PRODUCT scope");
        }
        if (request.scopeType() == PromotionScopeType.CATEGORY && request.targetCategoryIdsOrEmpty().isEmpty()) {
            throw new ValidationException("targetCategoryIds is required for CATEGORY scope");
        }
        if ((request.scopeType() == PromotionScopeType.VENDOR || !actorScope.isPlatformPrivileged())
                && request.vendorId() == null
                && (existing == null || existing.getVendorId() == null)) {
            throw new ValidationException("vendorId is required for vendor-scoped promotions");
        }
        if (!actorScope.isPlatformPrivileged() && request.fundingSource() != PromotionFundingSource.VENDOR) {
            throw new ValidationException("Vendor-scoped users can only create vendor-funded promotions");
        }
    }

    private boolean requiresApproval(PromotionCampaign campaign) {
        return campaign.getApprovalStatus() != null && campaign.getApprovalStatus() != PromotionApprovalStatus.NOT_REQUIRED;
    }

    private void applyRequest(PromotionCampaign campaign, UpsertPromotionRequest request) {
        campaign.setName(request.name().trim());
        campaign.setDescription(request.description().trim());
        campaign.setVendorId(request.vendorId());
        campaign.setScopeType(request.scopeType());
        campaign.setApplicationLevel(request.applicationLevel());
        campaign.setBenefitType(request.benefitType());
        campaign.setBenefitValue(request.benefitType() == PromotionBenefitType.BUY_X_GET_Y
                ? null
                : normalizeNullableMoney(request.benefitValue()));
        campaign.setBuyQuantity(request.benefitType() == PromotionBenefitType.BUY_X_GET_Y ? request.buyQuantity() : null);
        campaign.setGetQuantity(request.benefitType() == PromotionBenefitType.BUY_X_GET_Y ? request.getQuantity() : null);
        campaign.setSpendTiers(request.benefitType() == PromotionBenefitType.TIERED_SPEND
                ? request.spendTiersOrEmpty().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(tier -> normalizeNullableMoney(tier.thresholdAmount())))
                .map(tier -> new PromotionSpendTier(
                        normalizeNullableMoney(tier.thresholdAmount()),
                        normalizeNullableMoney(tier.discountAmount())
                ))
                .toList()
                : new ArrayList<>());
        campaign.setMinimumOrderAmount(normalizeNullableMoney(request.minimumOrderAmount()));
        campaign.setMaximumDiscountAmount(normalizeNullableMoney(request.maximumDiscountAmount()));
        campaign.setBudgetAmount(normalizeNullableMoney(request.budgetAmount()));
        if (campaign.getBurnedBudgetAmount() == null) {
            campaign.setBurnedBudgetAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            campaign.setBurnedBudgetAmount(normalizeNullableMoney(campaign.getBurnedBudgetAmount()));
        }
        campaign.setFundingSource(request.fundingSource());
        campaign.setStackable(request.stackable());
        campaign.setExclusive(request.exclusive());
        campaign.setStackingGroup(trimToNull(request.stackingGroup()));
        campaign.setMaxStackCount(request.maxStackCount());
        campaign.setAutoApply(request.autoApply());
        campaign.setPriority(request.priority() == null ? 100 : request.priority());
        campaign.setTargetSegments(trimToNull(request.targetSegments()));
        campaign.setFlashSale(request.flashSale());
        campaign.setFlashSaleStartAt(request.flashSale() ? request.flashSaleStartAt() : null);
        campaign.setFlashSaleEndAt(request.flashSale() ? request.flashSaleEndAt() : null);
        campaign.setFlashSaleMaxRedemptions(request.flashSale() ? request.flashSaleMaxRedemptions() : null);
        campaign.setStartsAt(request.startsAt());
        campaign.setEndsAt(request.endsAt());
        campaign.setTimezone(trimToNull(request.timezone()));
        campaign.setTargetProductIds(new LinkedHashSet<>(request.targetProductIdsOrEmpty()));
        campaign.setTargetCategoryIds(new LinkedHashSet<>(request.targetCategoryIdsOrEmpty()));
    }

    private BigDecimal normalizeNullableMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private PromotionResponse toResponse(PromotionCampaign entity) {
        return new PromotionResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVendorId(),
                entity.getScopeType(),
                entity.getTargetProductIds() == null ? Set.of() : Set.copyOf(entity.getTargetProductIds()),
                entity.getTargetCategoryIds() == null ? Set.of() : Set.copyOf(entity.getTargetCategoryIds()),
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
                normalizeNullableMoney(entity.getBudgetAmount()),
                normalizeNullableMoney(entity.getBurnedBudgetAmount()),
                remainingBudgetAmount(entity),
                entity.getFundingSource(),
                entity.isStackable(),
                entity.isExclusive(),
                entity.getStackingGroup(),
                entity.getMaxStackCount(),
                entity.isAutoApply(),
                entity.getPriority(),
                entity.getTargetSegments(),
                entity.isFlashSale(),
                entity.getFlashSaleStartAt(),
                entity.getFlashSaleEndAt(),
                entity.getFlashSaleMaxRedemptions(),
                entity.getFlashSaleRedemptionCount(),
                entity.getLifecycleStatus(),
                entity.getApprovalStatus(),
                entity.getApprovalNote(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getTimezone(),
                entity.getCreatedByUserSub(),
                entity.getUpdatedByUserSub(),
                entity.getSubmittedByUserSub(),
                entity.getApprovedByUserSub(),
                entity.getRejectedByUserSub(),
                entity.getSubmittedAt(),
                entity.getApprovedAt(),
                entity.getRejectedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private BigDecimal remainingBudgetAmount(PromotionCampaign entity) {
        if (entity == null || entity.getBudgetAmount() == null) {
            return null;
        }
        BigDecimal budget = normalizeNullableMoney(entity.getBudgetAmount());
        BigDecimal burned = normalizeNullableMoney(entity.getBurnedBudgetAmount());
        BigDecimal remaining = budget.subtract(burned == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : burned);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return remaining.setScale(2, RoundingMode.HALF_UP);
    }
}
