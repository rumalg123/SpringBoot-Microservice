package com.rumal.product_service.service;

import com.rumal.product_service.client.InventoryClient;
import com.rumal.product_service.client.VendorOperationalStateClient;
import com.rumal.product_service.dto.SearchProductIndexRequest;
import com.rumal.product_service.dto.StockAvailabilitySummary;
import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductCatalogRead;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.repo.ProductCatalogReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSearchIndexPayloadFactoryTests {

    private final ProductCatalogReadRepository productCatalogReadRepository = mock(ProductCatalogReadRepository.class);
    private final VendorOperationalStateClient vendorOperationalStateClient = mock(VendorOperationalStateClient.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);

    @Test
    void buildMarksNonBackorderableOutOfStockProductsInactiveForSearch() {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        ProductSearchIndexPayloadFactory factory = new ProductSearchIndexPayloadFactory(
                productCatalogReadRepository,
                vendorOperationalStateClient,
                inventoryClient
        );
        ReflectionTestUtils.setField(factory, "internalAuthSharedSecret", "shared-secret");
        ReflectionTestUtils.setField(factory, "hideOutOfStock", true);

        when(productCatalogReadRepository.findById(productId)).thenReturn(Optional.of(catalogRow(productId, vendorId)));
        when(vendorOperationalStateClient.getState(vendorId, "shared-secret")).thenReturn(
                new VendorOperationalStateResponse(vendorId, "Vendor", true, false, "VERIFIED", true, true, true)
        );
        when(inventoryClient.getStockSummary(productId)).thenReturn(
                new StockAvailabilitySummary(productId, 0, 5, 5, false, "OUT_OF_STOCK")
        );

        Optional<SearchProductIndexRequest> payload = factory.build(productId);

        assertTrue(payload.isPresent());
        assertEquals(false, payload.get().active());
        assertEquals(0, payload.get().stockAvailable());
        assertEquals("OUT_OF_STOCK", payload.get().stockStatus());
        assertEquals(false, payload.get().backorderable());
    }

    @Test
    void buildKeepsBackorderableProductsSearchableWhenAvailableHitsZero() {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        ProductSearchIndexPayloadFactory factory = new ProductSearchIndexPayloadFactory(
                productCatalogReadRepository,
                vendorOperationalStateClient,
                inventoryClient
        );
        ReflectionTestUtils.setField(factory, "internalAuthSharedSecret", "shared-secret");
        ReflectionTestUtils.setField(factory, "hideOutOfStock", true);

        when(productCatalogReadRepository.findById(productId)).thenReturn(Optional.of(catalogRow(productId, vendorId)));
        when(vendorOperationalStateClient.getState(vendorId, "shared-secret")).thenReturn(
                new VendorOperationalStateResponse(vendorId, "Vendor", true, false, "VERIFIED", true, true, true)
        );
        when(inventoryClient.getStockSummary(productId)).thenReturn(
                new StockAvailabilitySummary(productId, 0, 5, 5, true, "BACKORDER")
        );

        Optional<SearchProductIndexRequest> payload = factory.build(productId);

        assertTrue(payload.isPresent());
        assertEquals(true, payload.get().active());
        assertEquals(0, payload.get().stockAvailable());
        assertEquals("BACKORDER", payload.get().stockStatus());
        assertEquals(true, payload.get().backorderable());
    }

    private ProductCatalogRead catalogRow(UUID productId, UUID vendorId) {
        ProductCatalogRead row = new ProductCatalogRead();
        row.setId(productId);
        row.setVendorId(vendorId);
        row.setSlug("product-slug");
        row.setName("Product");
        row.setShortDescription("Short");
        row.setBrandName("Brand");
        row.setMainImage("main.jpg");
        row.setRegularPrice(new BigDecimal("10.00"));
        row.setDiscountedPrice(new BigDecimal("8.00"));
        row.setSellingPrice(new BigDecimal("8.00"));
        row.setSku("SKU-1");
        row.setMainCategory("Category");
        row.setSubCategoryTokens("Sub");
        row.setCategoryTokens("Category|Sub");
        row.setProductType(ProductType.SINGLE);
        row.setViewCount(1L);
        row.setSoldCount(2L);
        row.setActive(true);
        row.setDeleted(false);
        row.setApprovalStatus(ApprovalStatus.APPROVED);
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        return row;
    }
}
