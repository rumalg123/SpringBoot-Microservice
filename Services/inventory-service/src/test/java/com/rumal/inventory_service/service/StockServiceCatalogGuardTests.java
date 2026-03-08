package com.rumal.inventory_service.service;

import com.rumal.inventory_service.client.OrderClient;
import com.rumal.inventory_service.dto.BulkStockImportResponse;
import com.rumal.inventory_service.dto.OrderStatusSnapshot;
import com.rumal.inventory_service.dto.StockAvailabilitySummary;
import com.rumal.inventory_service.dto.StockItemCreateRequest;
import com.rumal.inventory_service.entity.CatalogProduct;
import com.rumal.inventory_service.entity.ReservationStatus;
import com.rumal.inventory_service.entity.StockItem;
import com.rumal.inventory_service.entity.StockReservation;
import com.rumal.inventory_service.entity.StockStatus;
import com.rumal.inventory_service.entity.Warehouse;
import com.rumal.inventory_service.entity.WarehouseType;
import com.rumal.inventory_service.exception.ServiceUnavailableException;
import com.rumal.inventory_service.exception.ValidationException;
import com.rumal.inventory_service.repo.StockItemRepository;
import com.rumal.inventory_service.repo.StockMovementRepository;
import com.rumal.inventory_service.repo.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceCatalogGuardTests {

    private final StockItemRepository stockItemRepository = mock(StockItemRepository.class);
    private final StockReservationRepository stockReservationRepository = mock(StockReservationRepository.class);
    private final StockMovementRepository stockMovementRepository = mock(StockMovementRepository.class);
    private final CatalogProductService catalogProductService = mock(CatalogProductService.class);
    private final WarehouseService warehouseService = mock(WarehouseService.class);
    private final OrderClient orderClient = mock(OrderClient.class);
    private final InventoryProductSearchSyncOutboxService inventoryProductSearchSyncOutboxService = mock(InventoryProductSearchSyncOutboxService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final StockService stockService = new StockService(
            stockItemRepository,
            stockReservationRepository,
            stockMovementRepository,
            catalogProductService,
            warehouseService,
            orderClient,
            inventoryProductSearchSyncOutboxService,
            transactionManager
    );

    @Test
    void getStockSummaryIgnoresStockItemsOwnedByAnotherVendor() {
        UUID productId = UUID.randomUUID();
        UUID correctVendorId = UUID.randomUUID();
        UUID rogueVendorId = UUID.randomUUID();

        when(catalogProductService.findByProductId(productId)).thenReturn(CatalogProduct.builder()
                .productId(productId)
                .vendorId(correctVendorId)
                .name("Registered Product")
                .sku("SKU-1")
                .active(true)
                .deleted(false)
                .build());

        when(stockItemRepository.findByProductId(productId)).thenReturn(List.of(
                StockItem.builder()
                        .productId(productId)
                        .vendorId(correctVendorId)
                        .quantityOnHand(5)
                        .quantityReserved(1)
                        .quantityAvailable(4)
                        .stockStatus(StockStatus.IN_STOCK)
                        .build(),
                StockItem.builder()
                        .productId(productId)
                        .vendorId(rogueVendorId)
                        .quantityOnHand(50)
                        .quantityReserved(0)
                        .quantityAvailable(50)
                        .stockStatus(StockStatus.IN_STOCK)
                        .build()
        ));

        StockAvailabilitySummary summary = stockService.getStockSummary(productId);

        assertEquals(4, summary.totalAvailable());
        assertEquals(5, summary.totalOnHand());
        assertEquals(1, summary.totalReserved());
    }

    @Test
    void getStockSummaryReturnsOutOfStockWhenCatalogRegistrationIsMissing() {
        UUID productId = UUID.randomUUID();

        when(catalogProductService.findByProductId(productId)).thenReturn(null);
        when(stockItemRepository.findByProductId(productId)).thenReturn(List.of(
                StockItem.builder()
                        .productId(productId)
                        .vendorId(UUID.randomUUID())
                        .quantityOnHand(25)
                        .quantityReserved(0)
                        .quantityAvailable(25)
                        .stockStatus(StockStatus.IN_STOCK)
                        .build()
        ));

        StockAvailabilitySummary summary = stockService.getStockSummary(productId);

        assertEquals(0, summary.totalAvailable());
        assertEquals(0, summary.totalOnHand());
        assertEquals(StockStatus.OUT_OF_STOCK.name(), summary.stockStatus());
    }

    @Test
    void confirmReservationIsIdempotentWhenAlreadyConfirmed() {
        UUID orderId = UUID.randomUUID();

        when(stockReservationRepository.findByOrderIdAndStatusWithStockItemForUpdate(orderId, ReservationStatus.RESERVED))
                .thenReturn(List.of());
        when(stockReservationRepository.existsByOrderIdAndStatusIn(
                orderId,
                EnumSet.of(ReservationStatus.CONFIRMED)
        )).thenReturn(true);

        assertDoesNotThrow(() -> stockService.confirmReservation(orderId));
        verify(stockItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void expireStaleReservationsBatchFailsClosedWhenOrderStatusCannotBeVerified() {
        UUID reservationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Warehouse")
                .vendorId(vendorId)
                .warehouseType(WarehouseType.VENDOR_OWNED)
                .build();

        StockItem stockItem = StockItem.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .vendorId(vendorId)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(1)
                .quantityAvailable(4)
                .stockStatus(StockStatus.IN_STOCK)
                .build();

        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .orderId(orderId)
                .productId(productId)
                .stockItem(stockItem)
                .quantityReserved(1)
                .status(ReservationStatus.RESERVED)
                .reservedAt(Instant.now().minusSeconds(120))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        when(stockReservationRepository.findExpiredReservations(
                eq(ReservationStatus.RESERVED),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(reservation)));
        when(stockReservationRepository.findByIdAndStatusWithStockItemForUpdate(
                reservationId,
                ReservationStatus.RESERVED
        )).thenReturn(Optional.of(reservation));
        when(orderClient.getOrderStatus(orderId))
                .thenThrow(new ServiceUnavailableException("Order service unavailable", new RuntimeException("boom")));

        int expired = stockService.expireStaleReservationsBatch(10);

        assertEquals(0, expired);
        verify(stockItemRepository, never()).findByIdForUpdate(stockItem.getId());
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void adjustStockRejectsNegativeAvailableForNonBackorderableItems() {
        UUID stockItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Warehouse")
                .vendorId(vendorId)
                .warehouseType(WarehouseType.VENDOR_OWNED)
                .build();

        StockItem stockItem = StockItem.builder()
                .id(stockItemId)
                .productId(productId)
                .vendorId(vendorId)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(4)
                .quantityAvailable(1)
                .backorderable(false)
                .stockStatus(StockStatus.LOW_STOCK)
                .build();

        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(stockItem));

        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ValidationException.class,
                () -> stockService.adjustStock(stockItemId, -2, "damage", "vendor", "user-1")
        );

        assertTrue(ex.getMessage().contains("non-backorderable stock cannot go below zero available quantity"));
        verify(stockItemRepository, never()).save(any(StockItem.class));
        verify(inventoryProductSearchSyncOutboxService, never()).enqueue(any());
    }

    @Test
    void bulkImportRejectsExistingRowsThatWouldBecomeNegativeAvailableWhenBackorderingIsDisabled() {
        UUID productId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Warehouse")
                .vendorId(vendorId)
                .warehouseType(WarehouseType.VENDOR_OWNED)
                .build();

        CatalogProduct catalogProduct = CatalogProduct.builder()
                .productId(productId)
                .vendorId(vendorId)
                .name("Product")
                .sku("SKU-1")
                .active(true)
                .deleted(false)
                .build();

        StockItem existing = StockItem.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .vendorId(vendorId)
                .warehouse(warehouse)
                .sku("SKU-1")
                .quantityOnHand(10)
                .quantityReserved(6)
                .quantityAvailable(4)
                .backorderable(false)
                .stockStatus(StockStatus.IN_STOCK)
                .build();

        when(catalogProductService.requireOwnedProduct(productId, vendorId)).thenReturn(catalogProduct);
        when(stockItemRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)).thenReturn(Optional.of(existing));

        BulkStockImportResponse response = stockService.bulkImport(
                List.of(new StockItemCreateRequest(productId, vendorId, warehouseId, "SKU-1", 5, 1, false)),
                "admin",
                "user-1"
        );

        assertEquals(1, response.totalProcessed());
        assertEquals(0, response.created());
        assertEquals(0, response.updated());
        assertEquals(1, response.errors().size());
        verify(stockItemRepository, never()).save(any(StockItem.class));
        verify(inventoryProductSearchSyncOutboxService, never()).enqueue(any());
    }
}
