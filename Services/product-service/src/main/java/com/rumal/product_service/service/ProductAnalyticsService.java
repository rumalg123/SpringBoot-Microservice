package com.rumal.product_service.service;

import com.rumal.product_service.dto.analytics.*;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class ProductAnalyticsService {

    private final ProductRepository productRepository;

    public ProductPlatformSummary getPlatformSummary() {
        long active = productRepository.countByDeletedFalseAndActiveTrue();
        long draft = productRepository.countByDeletedFalseAndApprovalStatus(ApprovalStatus.DRAFT);
        long pending = productRepository.countByDeletedFalseAndApprovalStatus(ApprovalStatus.PENDING_REVIEW);
        long totalViews = productRepository.sumTotalViews();
        long totalSold = productRepository.sumTotalSold();

        // Count all non-deleted products by summing grouped counts
        List<Object[]> grouped = productRepository.countByApprovalStatusGrouped();
        long totalProducts = 0;
        for (Object[] row : grouped) {
            totalProducts += ((Number) row[1]).longValue();
        }

        return new ProductPlatformSummary(totalProducts, active, draft, pending, totalViews, totalSold);
    }

    public List<ProductViewEntry> getTopViewed(int limit) {
        return productRepository.findTopByViews(PageRequest.of(0, limit)).stream()
            .map(r -> new ProductViewEntry((UUID) r[0], (String) r[1], (UUID) r[2], ((Number) r[3]).longValue()))
            .toList();
    }

    public List<ProductSoldEntry> getTopSold(int limit) {
        return productRepository.findTopBySold(PageRequest.of(0, limit)).stream()
            .map(r -> new ProductSoldEntry((UUID) r[0], (String) r[1], (UUID) r[2], ((Number) r[3]).longValue()))
            .toList();
    }

    public Map<String, Long> getApprovalBreakdown() {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Object[] row : productRepository.countByApprovalStatusGrouped()) {
            breakdown.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return breakdown;
    }

    public VendorProductSummary getVendorSummary(UUID vendorId) {
        long total = productRepository.countByVendorIdAndDeletedFalse(vendorId);
        long active = productRepository.countByVendorIdAndDeletedFalseAndActiveTrue(vendorId);
        long views = productRepository.sumViewsByVendorId(vendorId);
        long sold = productRepository.sumSoldByVendorId(vendorId);
        return new VendorProductSummary(vendorId, total, active, views, sold);
    }
}
