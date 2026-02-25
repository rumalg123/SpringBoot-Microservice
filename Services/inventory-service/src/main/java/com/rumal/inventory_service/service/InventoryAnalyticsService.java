package com.rumal.inventory_service.service;

import com.rumal.inventory_service.dto.analytics.*;
import com.rumal.inventory_service.entity.StockStatus;
import com.rumal.inventory_service.repo.StockItemRepository;
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
public class InventoryAnalyticsService {

    private final StockItemRepository stockItemRepository;

    public InventoryHealthSummary getPlatformHealth() {
        long total = stockItemRepository.count();
        long inStock = stockItemRepository.countByStockStatus(StockStatus.IN_STOCK);
        long lowStock = stockItemRepository.countByStockStatus(StockStatus.LOW_STOCK);
        long outOfStock = stockItemRepository.countByStockStatus(StockStatus.OUT_OF_STOCK);
        long backorder = stockItemRepository.countByStockStatus(StockStatus.BACKORDER);
        long onHand = stockItemRepository.sumTotalQuantityOnHand();
        long reserved = stockItemRepository.sumTotalQuantityReserved();

        return new InventoryHealthSummary(total, inStock, lowStock, outOfStock,
            backorder, onHand, reserved);
    }

    public VendorInventoryHealth getVendorHealth(UUID vendorId) {
        long total = stockItemRepository.countByVendorId(vendorId);
        long inStock = stockItemRepository.countByVendorIdAndStockStatus(vendorId, StockStatus.IN_STOCK);
        long lowStock = stockItemRepository.countByVendorIdAndStockStatus(vendorId, StockStatus.LOW_STOCK);
        long outOfStock = stockItemRepository.countByVendorIdAndStockStatus(vendorId, StockStatus.OUT_OF_STOCK);

        return new VendorInventoryHealth(vendorId, total, inStock, lowStock, outOfStock);
    }

    public List<LowStockAlert> getLowStockAlerts(int limit) {
        return stockItemRepository.findLowStockAlerts(PageRequest.of(0, limit)).stream()
            .map(si -> new LowStockAlert(
                si.getProductId(), si.getVendorId(), si.getSku(),
                si.getQuantityAvailable(), si.getLowStockThreshold(),
                si.getStockStatus().name()))
            .toList();
    }
}
