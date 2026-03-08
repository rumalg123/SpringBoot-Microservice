package com.rumal.product_service.service;

import com.rumal.product_service.client.InventoryClient;
import com.rumal.product_service.client.VendorOperationalStateClient;
import com.rumal.product_service.dto.SearchProductIndexRequest;
import com.rumal.product_service.dto.StockAvailabilitySummary;
import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductCatalogRead;
import com.rumal.product_service.exception.ServiceUnavailableException;
import com.rumal.product_service.repo.ProductCatalogReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductSearchIndexPayloadFactory {

    private final ProductCatalogReadRepository productCatalogReadRepository;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final InventoryClient inventoryClient;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Value("${product.search.hide-out-of-stock:true}")
    private boolean hideOutOfStock;

    public Optional<SearchProductIndexRequest> build(UUID productId) {
        if (productId == null) {
            return Optional.empty();
        }

        ProductCatalogRead row = productCatalogReadRepository.findById(productId).orElse(null);
        if (row == null || row.isDeleted() || !row.isActive() || row.getApprovalStatus() != ApprovalStatus.APPROVED) {
            return Optional.empty();
        }

        if (row.getVendorId() != null) {
            VendorOperationalStateResponse vendorState = vendorOperationalStateClient.getState(row.getVendorId(), requireInternalAuth());
            if (vendorState == null || "UNKNOWN".equalsIgnoreCase(vendorState.status())) {
                throw new ServiceUnavailableException("Vendor visibility state is unavailable for product " + productId);
            }
            if (!vendorState.storefrontVisible()) {
                return Optional.empty();
            }
        }

        StockAvailabilitySummary stockSummary = requireStockSummary(productId);
        boolean searchable = isSearchVisible(stockSummary);

        return Optional.of(new SearchProductIndexRequest(
                row.getId(),
                row.getSlug(),
                row.getName(),
                row.getShortDescription(),
                row.getBrandName(),
                row.getMainImage(),
                row.getRegularPrice(),
                row.getDiscountedPrice(),
                row.getSellingPrice(),
                row.getSku(),
                row.getMainCategory(),
                decodeTokenSet(row.getSubCategoryTokens()),
                decodeTokenSet(row.getCategoryTokens()),
                row.getProductType() == null ? null : row.getProductType().name(),
                row.getVendorId(),
                row.getViewCount(),
                row.getSoldCount(),
                row.isActive() && (!hideOutOfStock || searchable),
                stockSummary.totalAvailable(),
                stockSummary.stockStatus(),
                stockSummary.backorderable(),
                List.of(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        ));
    }

    private StockAvailabilitySummary requireStockSummary(UUID productId) {
        StockAvailabilitySummary summary = inventoryClient.getStockSummary(productId);
        if (summary == null) {
            throw new ServiceUnavailableException("Inventory stock summary is unavailable for product " + productId);
        }
        return summary;
    }

    private boolean isSearchVisible(StockAvailabilitySummary stockSummary) {
        if (stockSummary == null) {
            return true;
        }
        if (Boolean.TRUE.equals(stockSummary.backorderable())) {
            return true;
        }
        Integer totalAvailable = stockSummary.totalAvailable();
        if (totalAvailable != null) {
            return totalAvailable > 0;
        }
        String stockStatus = stockSummary.stockStatus();
        return stockStatus == null || !"OUT_OF_STOCK".equalsIgnoreCase(stockStatus);
    }

    private Set<String> decodeTokenSet(String encodedTokens) {
        if (!StringUtils.hasText(encodedTokens)) {
            return Set.of();
        }
        String[] segments = encodedTokens.split("\\|");
        Set<String> values = new LinkedHashSet<>();
        for (String segment : segments) {
            if (StringUtils.hasText(segment)) {
                values.add(segment.trim());
            }
        }
        return Set.copyOf(values);
    }

    private String requireInternalAuth() {
        if (!StringUtils.hasText(internalAuthSharedSecret)) {
            throw new ServiceUnavailableException("INTERNAL_AUTH_SHARED_SECRET is not configured in product-service");
        }
        return internalAuthSharedSecret.trim();
    }
}
