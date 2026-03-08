package com.rumal.promotion_service.service;

import com.rumal.promotion_service.client.CustomerClient;
import com.rumal.promotion_service.client.OrderClient;
import com.rumal.promotion_service.dto.CustomerPromotionEligibilityResponse;
import com.rumal.promotion_service.dto.CustomerSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerPromotionEligibilityService {

    private final CustomerClient customerClient;
    private final OrderClient orderClient;

    @Value("${promotion.customer.new-user-max-account-age-days:30}")
    private long newUserMaxAccountAgeDays;

    public CustomerPromotionEligibilityProfile resolveProfile(UUID customerId, Instant now) {
        if (customerId == null) {
            return CustomerPromotionEligibilityProfile.anonymous();
        }

        Instant evaluationTime = now == null ? Instant.now() : now;
        CustomerSummary customer = customerClient.getCustomer(customerId);
        CustomerPromotionEligibilityResponse orderEligibility = orderClient.getCustomerPromotionEligibility(customerId);

        boolean newUser = isNewUser(customer, orderEligibility, evaluationTime);
        Set<String> derivedSegments = new LinkedHashSet<>();
        derivedSegments.add(newUser ? "NEW_USER" : "EXISTING_CUSTOMER");

        String loyaltyTier = normalizeSegment(customer == null ? null : customer.loyaltyTier());
        if (loyaltyTier != null) {
            derivedSegments.add("LOYALTY_" + loyaltyTier);
        }

        return new CustomerPromotionEligibilityProfile(
                customerId,
                customer == null ? null : customer.createdAt(),
                loyaltyTier,
                orderEligibility == null ? 0 : orderEligibility.qualifyingOrderCount(),
                newUser,
                Set.copyOf(derivedSegments)
        );
    }

    private boolean isNewUser(
            CustomerSummary customer,
            CustomerPromotionEligibilityResponse orderEligibility,
            Instant evaluationTime
    ) {
        if (customer == null || customer.createdAt() == null) {
            return false;
        }
        long maxAgeDays = Math.max(1L, newUserMaxAccountAgeDays);
        Instant newestAllowedCreatedAt = evaluationTime.minus(Duration.ofDays(maxAgeDays));
        if (customer.createdAt().isBefore(newestAllowedCreatedAt)) {
            return false;
        }
        return orderEligibility == null || !orderEligibility.hasQualifyingOrders();
    }

    private String normalizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    public record CustomerPromotionEligibilityProfile(
            UUID customerId,
            Instant createdAt,
            String loyaltyTier,
            long qualifyingOrderCount,
            boolean newUser,
            Set<String> derivedSegments
    ) {
        private static CustomerPromotionEligibilityProfile anonymous() {
            return new CustomerPromotionEligibilityProfile(
                    null,
                    null,
                    null,
                    0,
                    false,
                    Set.of()
            );
        }
    }
}
