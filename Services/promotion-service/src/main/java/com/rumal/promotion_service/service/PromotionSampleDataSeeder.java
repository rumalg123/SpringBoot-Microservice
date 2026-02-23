package com.rumal.promotion_service.service;

import com.rumal.promotion_service.entity.CouponCode;
import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.repo.CouponCodeRepository;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "promotion.sample-data", name = "enabled", havingValue = "true")
public class PromotionSampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromotionSampleDataSeeder.class);

    private static final String SAMPLE_SEED_USER_SUB = "system_sample_seed";

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final CouponCodeRepository couponCodeRepository;

    @Value("${promotion.sample-data.vendor-id:}")
    private String vendorIdRaw;

    @Value("${promotion.sample-data.product-id:}")
    private String productIdRaw;

    @Value("${promotion.sample-data.category-id:}")
    private String categoryIdRaw;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        Instant startsAt = now.minusSeconds(24 * 60 * 60);
        Instant endsAt = now.plusSeconds(30L * 24 * 60 * 60);

        Map<String, PromotionCampaign> byName = loadByName();
        int createdCampaigns = 0;
        int createdCoupons = 0;

        if (ensureCampaign(
                byName,
                "Sample Auto Cart 10% Off (Min 50)",
                campaign -> {
                    campaign.setDescription("Auto-apply platform cart discount for local testing (10% off orders >= 50).");
                    campaign.setVendorId(null);
                    campaign.setScopeType(PromotionScopeType.ORDER);
                    campaign.setApplicationLevel(PromotionApplicationLevel.CART);
                    campaign.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
                    campaign.setBenefitValue(new BigDecimal("10.00"));
                    campaign.setMinimumOrderAmount(new BigDecimal("50.00"));
                    campaign.setMaximumDiscountAmount(new BigDecimal("25.00"));
                    campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                    campaign.setStackable(true);
                    campaign.setExclusive(false);
                    campaign.setAutoApply(true);
                    campaign.setPriority(100);
                    campaign.setTargetProductIds(Set.of());
                    campaign.setTargetCategoryIds(Set.of());
                    campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                    campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                    campaign.setStartsAt(startsAt);
                    campaign.setEndsAt(endsAt);
                }
        )) {
            createdCampaigns++;
        }

        if (ensureCampaign(
                byName,
                "Sample Free Shipping (Min 25)",
                campaign -> {
                    campaign.setDescription("Auto-apply free shipping promotion for local testing (order scope).");
                    campaign.setVendorId(null);
                    campaign.setScopeType(PromotionScopeType.ORDER);
                    campaign.setApplicationLevel(PromotionApplicationLevel.SHIPPING);
                    campaign.setBenefitType(PromotionBenefitType.FREE_SHIPPING);
                    campaign.setBenefitValue(BigDecimal.ZERO.setScale(2));
                    campaign.setMinimumOrderAmount(new BigDecimal("25.00"));
                    campaign.setMaximumDiscountAmount(null);
                    campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                    campaign.setStackable(true);
                    campaign.setExclusive(false);
                    campaign.setAutoApply(true);
                    campaign.setPriority(150);
                    campaign.setTargetProductIds(Set.of());
                    campaign.setTargetCategoryIds(Set.of());
                    campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                    campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                    campaign.setStartsAt(startsAt);
                    campaign.setEndsAt(endsAt);
                }
        )) {
            createdCampaigns++;
        }

        if (ensureCampaign(
                byName,
                "Sample Coupon Campaign - SAVE10",
                campaign -> {
                    campaign.setDescription("Coupon campaign for local testing (fixed 10 off orders >= 80).");
                    campaign.setVendorId(null);
                    campaign.setScopeType(PromotionScopeType.ORDER);
                    campaign.setApplicationLevel(PromotionApplicationLevel.CART);
                    campaign.setBenefitType(PromotionBenefitType.FIXED_AMOUNT_OFF);
                    campaign.setBenefitValue(new BigDecimal("10.00"));
                    campaign.setMinimumOrderAmount(new BigDecimal("80.00"));
                    campaign.setMaximumDiscountAmount(new BigDecimal("10.00"));
                    campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                    campaign.setStackable(true);
                    campaign.setExclusive(false);
                    campaign.setAutoApply(false);
                    campaign.setPriority(200);
                    campaign.setTargetProductIds(Set.of());
                    campaign.setTargetCategoryIds(Set.of());
                    campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                    campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                    campaign.setStartsAt(startsAt);
                    campaign.setEndsAt(endsAt);
                }
        )) {
            createdCampaigns++;
        }
        PromotionCampaign save10Campaign = byName.get(normalizeName("Sample Coupon Campaign - SAVE10"));
        if (ensureCoupon(save10Campaign, "SAVE10", 900, startsAt, endsAt)) {
            createdCoupons++;
        }

        if (ensureCampaign(
                byName,
                "Sample Coupon Campaign - FREESHIP",
                campaign -> {
                    campaign.setDescription("Coupon-based free shipping for local testing.");
                    campaign.setVendorId(null);
                    campaign.setScopeType(PromotionScopeType.ORDER);
                    campaign.setApplicationLevel(PromotionApplicationLevel.SHIPPING);
                    campaign.setBenefitType(PromotionBenefitType.FREE_SHIPPING);
                    campaign.setBenefitValue(BigDecimal.ZERO.setScale(2));
                    campaign.setMinimumOrderAmount(new BigDecimal("30.00"));
                    campaign.setMaximumDiscountAmount(null);
                    campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                    campaign.setStackable(true);
                    campaign.setExclusive(false);
                    campaign.setAutoApply(false);
                    campaign.setPriority(210);
                    campaign.setTargetProductIds(Set.of());
                    campaign.setTargetCategoryIds(Set.of());
                    campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                    campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                    campaign.setStartsAt(startsAt);
                    campaign.setEndsAt(endsAt);
                }
        )) {
            createdCampaigns++;
        }
        PromotionCampaign freeShipCouponCampaign = byName.get(normalizeName("Sample Coupon Campaign - FREESHIP"));
        if (ensureCoupon(freeShipCouponCampaign, "FREESHIP", 900, startsAt, endsAt)) {
            createdCoupons++;
        }

        UUID vendorId = parseOptionalUuid(vendorIdRaw, "promotion.sample-data.vendor-id");
        if (vendorId != null) {
            if (ensureCampaign(
                    byName,
                    "Sample Vendor Flash Sale 15% (Configured Vendor)",
                    campaign -> {
                        campaign.setDescription("Vendor-scoped line-item flash sale for the configured sample vendor.");
                        campaign.setVendorId(vendorId);
                        campaign.setScopeType(PromotionScopeType.VENDOR);
                        campaign.setApplicationLevel(PromotionApplicationLevel.LINE_ITEM);
                        campaign.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
                        campaign.setBenefitValue(new BigDecimal("15.00"));
                        campaign.setMinimumOrderAmount(null);
                        campaign.setMaximumDiscountAmount(new BigDecimal("50.00"));
                        campaign.setFundingSource(PromotionFundingSource.VENDOR);
                        campaign.setStackable(true);
                        campaign.setExclusive(false);
                        campaign.setAutoApply(true);
                        campaign.setPriority(80);
                        campaign.setTargetProductIds(Set.of());
                        campaign.setTargetCategoryIds(Set.of());
                        campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                        campaign.setApprovalStatus(PromotionApprovalStatus.APPROVED);
                        campaign.setStartsAt(startsAt);
                        campaign.setEndsAt(endsAt);
                    }
            )) {
                createdCampaigns++;
            }
        }

        UUID productId = parseOptionalUuid(productIdRaw, "promotion.sample-data.product-id");
        if (productId != null) {
            if (ensureCampaign(
                    byName,
                    "Sample Product Spotlight 20% (Configured Product)",
                    campaign -> {
                        campaign.setDescription("Product-scoped line-item promo for a configured product id.");
                        campaign.setVendorId(null);
                        campaign.setScopeType(PromotionScopeType.PRODUCT);
                        campaign.setApplicationLevel(PromotionApplicationLevel.LINE_ITEM);
                        campaign.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
                        campaign.setBenefitValue(new BigDecimal("20.00"));
                        campaign.setMinimumOrderAmount(null);
                        campaign.setMaximumDiscountAmount(null);
                        campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                        campaign.setStackable(true);
                        campaign.setExclusive(false);
                        campaign.setAutoApply(true);
                        campaign.setPriority(60);
                        campaign.setTargetProductIds(Set.of(productId));
                        campaign.setTargetCategoryIds(Set.of());
                        campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                        campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                        campaign.setStartsAt(startsAt);
                        campaign.setEndsAt(endsAt);
                    }
            )) {
                createdCampaigns++;
            }
        }

        UUID categoryId = parseOptionalUuid(categoryIdRaw, "promotion.sample-data.category-id");
        if (categoryId != null) {
            if (ensureCampaign(
                    byName,
                    "Sample Category Promo 12% (Configured Category)",
                    campaign -> {
                        campaign.setDescription("Category-scoped line-item promo for a configured category id.");
                        campaign.setVendorId(null);
                        campaign.setScopeType(PromotionScopeType.CATEGORY);
                        campaign.setApplicationLevel(PromotionApplicationLevel.LINE_ITEM);
                        campaign.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
                        campaign.setBenefitValue(new BigDecimal("12.00"));
                        campaign.setMinimumOrderAmount(null);
                        campaign.setMaximumDiscountAmount(null);
                        campaign.setFundingSource(PromotionFundingSource.PLATFORM);
                        campaign.setStackable(true);
                        campaign.setExclusive(false);
                        campaign.setAutoApply(true);
                        campaign.setPriority(70);
                        campaign.setTargetProductIds(Set.of());
                        campaign.setTargetCategoryIds(Set.of(categoryId));
                        campaign.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
                        campaign.setApprovalStatus(PromotionApprovalStatus.NOT_REQUIRED);
                        campaign.setStartsAt(startsAt);
                        campaign.setEndsAt(endsAt);
                    }
            )) {
                createdCampaigns++;
            }
        }

        log.info(
                "Promotion sample-data seeding completed (createdCampaigns={}, createdCoupons={}, vendorIdConfigured={}, productIdConfigured={}, categoryIdConfigured={})",
                createdCampaigns,
                createdCoupons,
                vendorId != null,
                productId != null,
                categoryId != null
        );
    }

    private Map<String, PromotionCampaign> loadByName() {
        Map<String, PromotionCampaign> byName = new LinkedHashMap<>();
        for (PromotionCampaign campaign : promotionCampaignRepository.findAll()) {
            if (campaign != null && StringUtils.hasText(campaign.getName())) {
                byName.put(normalizeName(campaign.getName()), campaign);
            }
        }
        return byName;
    }

    private boolean ensureCampaign(
            Map<String, PromotionCampaign> byName,
            String name,
            java.util.function.Consumer<PromotionCampaign> spec
    ) {
        String key = normalizeName(name);
        if (byName.containsKey(key)) {
            return false;
        }
        PromotionCampaign created = buildAndSaveCampaign(name, spec);
        byName.put(key, created);
        return true;
    }

    private PromotionCampaign buildAndSaveCampaign(
            String name,
            java.util.function.Consumer<PromotionCampaign> spec
    ) {
        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setName(name);
        campaign.setCreatedByUserSub(SAMPLE_SEED_USER_SUB);
        campaign.setUpdatedByUserSub(SAMPLE_SEED_USER_SUB);
        spec.accept(campaign);
        return promotionCampaignRepository.save(campaign);
    }

    private boolean ensureCoupon(
            PromotionCampaign campaign,
            String code,
            Integer reservationTtlSeconds,
            Instant startsAt,
            Instant endsAt
    ) {
        if (campaign == null || campaign.getId() == null) {
            return false;
        }
        if (couponCodeRepository.existsByCodeIgnoreCase(code)) {
            return false;
        }
        CouponCode coupon = new CouponCode();
        coupon.setPromotion(campaign);
        coupon.setCode(code.trim().toUpperCase(Locale.ROOT));
        coupon.setActive(true);
        coupon.setMaxUses(null);
        coupon.setMaxUsesPerCustomer(1);
        coupon.setReservationTtlSeconds(reservationTtlSeconds);
        coupon.setStartsAt(startsAt);
        coupon.setEndsAt(endsAt);
        coupon.setCreatedByUserSub(SAMPLE_SEED_USER_SUB);
        coupon.setUpdatedByUserSub(SAMPLE_SEED_USER_SUB);
        couponCodeRepository.save(coupon);
        return true;
    }

    private UUID parseOptionalUuid(String raw, String propertyName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring invalid UUID for {}: {}", propertyName, raw);
            return null;
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
