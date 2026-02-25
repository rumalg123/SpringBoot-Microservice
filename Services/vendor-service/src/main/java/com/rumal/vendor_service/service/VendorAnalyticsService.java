package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.analytics.*;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.exception.ResourceNotFoundException;
import com.rumal.vendor_service.repo.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class VendorAnalyticsService {

    private final VendorRepository vendorRepository;

    public VendorPlatformSummary getPlatformSummary() {
        long total = vendorRepository.countByDeletedFalse();
        long active = vendorRepository.countByDeletedFalseAndStatus(VendorStatus.ACTIVE);
        long pending = vendorRepository.countByDeletedFalseAndStatus(VendorStatus.PENDING);
        long suspended = vendorRepository.countByDeletedFalseAndStatus(VendorStatus.SUSPENDED);
        long verified = vendorRepository.countByDeletedFalseAndVerifiedTrue();
        var avgCommission = vendorRepository.avgCommissionRate();
        var avgFulfillment = vendorRepository.avgFulfillmentRate();

        return new VendorPlatformSummary(total, active, pending, suspended,
            verified, avgCommission, avgFulfillment);
    }

    public List<VendorLeaderboardEntry> getLeaderboard(String sortBy, int limit) {
        List<Vendor> vendors = switch (sortBy.toUpperCase()) {
            case "RATING" -> vendorRepository.findTopVendorsByRating(PageRequest.of(0, limit));
            case "FULFILLMENT" -> vendorRepository.findTopVendorsByFulfillment(PageRequest.of(0, limit));
            default -> vendorRepository.findTopVendorsByOrdersCompleted(PageRequest.of(0, limit));
        };
        return vendors.stream()
            .map(v -> new VendorLeaderboardEntry(v.getId(), v.getName(),
                v.getTotalOrdersCompleted(), v.getAverageRating(),
                v.getFulfillmentRate(), v.getDisputeRate(), v.isVerified()))
            .toList();
    }

    public VendorPerformanceSummary getPerformance(UUID vendorId) {
        Vendor v = vendorRepository.findById(vendorId)
            .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + vendorId));
        return new VendorPerformanceSummary(v.getId(), v.getName(),
            v.getStatus().name(), v.getAverageRating(), v.getFulfillmentRate(),
            v.getDisputeRate(), v.getResponseTimeHours(),
            v.getTotalOrdersCompleted(), v.getCommissionRate());
    }
}
