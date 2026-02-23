package com.rumal.cart_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ShippingFeeCalculator {

    private final BigDecimal basePerVendor;
    private final BigDecimal perItemFee;
    private final BigDecimal internationalSurchargePerVendor;
    private final String domesticCountryCode;

    public ShippingFeeCalculator(
            @Value("${shipping.fee.base-per-vendor:4.99}") BigDecimal basePerVendor,
            @Value("${shipping.fee.per-item:0.80}") BigDecimal perItemFee,
            @Value("${shipping.fee.international-surcharge-per-vendor:3.50}") BigDecimal internationalSurchargePerVendor,
            @Value("${shipping.fee.domestic-country-code:US}") String domesticCountryCode
    ) {
        this.basePerVendor = normalizeMoney(basePerVendor);
        this.perItemFee = normalizeMoney(perItemFee);
        this.internationalSurchargePerVendor = normalizeMoney(internationalSurchargePerVendor);
        this.domesticCountryCode = normalizeCountry(domesticCountryCode);
    }

    public BigDecimal calculate(List<ShippingLine> lines, String destinationCountryCode) {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Set<UUID> vendorIds = new java.util.LinkedHashSet<>();
        int totalQuantity = 0;
        for (ShippingLine line : lines) {
            if (line == null || line.vendorId() == null || line.quantity() <= 0) {
                continue;
            }
            vendorIds.add(line.vendorId());
            totalQuantity += line.quantity();
        }
        if (vendorIds.isEmpty() || totalQuantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fee = basePerVendor.multiply(BigDecimal.valueOf(vendorIds.size()))
                .add(perItemFee.multiply(BigDecimal.valueOf(totalQuantity)));
        String destination = normalizeCountry(destinationCountryCode);
        if (destination != null && domesticCountryCode != null && !domesticCountryCode.equals(destination)) {
            fee = fee.add(internationalSurchargePerVendor.multiply(BigDecimal.valueOf(vendorIds.size())));
        }
        return normalizeMoney(fee);
    }

    private String normalizeCountry(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public record ShippingLine(UUID vendorId, int quantity) {
    }
}
