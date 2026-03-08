package com.rumal.inventory_service.service;

import com.rumal.inventory_service.client.OrderClient;
import com.rumal.inventory_service.dto.*;
import com.rumal.inventory_service.entity.*;
import com.rumal.inventory_service.exception.InsufficientStockException;
import com.rumal.inventory_service.exception.ResourceNotFoundException;
import com.rumal.inventory_service.exception.ServiceUnavailableException;
import com.rumal.inventory_service.exception.ValidationException;
import com.rumal.inventory_service.repo.StockItemRepository;
import com.rumal.inventory_service.repo.StockMovementRepository;
import com.rumal.inventory_service.repo.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CatalogProductService catalogProductService;
    private final WarehouseService warehouseService;
    private final OrderClient orderClient;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    @Transactional(readOnly = true)
    public List<StockCheckResult> checkAvailability(List<StockCheckRequest> requests) {
        List<StockCheckResult> results = new ArrayList<>();
        for (StockCheckRequest req : requests) {
            List<StockItem> items = stockItemRepository.findByProductIdWithActiveWarehouse(req.productId());
            int totalAvailable = items.stream()
                    .mapToInt(StockItem::getQuantityAvailable)
                    .sum();
            boolean backorderable = items.stream().anyMatch(StockItem::isBackorderable);
            boolean sufficient = totalAvailable >= req.quantity() || backorderable;
            String status = resolveAggregateStatus(totalAvailable, backorderable, items);
            results.add(new StockCheckResult(req.productId(), totalAvailable, sufficient, backorderable, status));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public boolean isReservationReadyForPayment(UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return stockReservationRepository.existsByOrderIdAndStatusIn(
                orderId,
                EnumSet.of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED)
        );
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public StockReservationResponse reserveForOrder(UUID orderId, List<StockCheckRequest> items, Instant expiresAt) {
        List<StockReservation> existing = stockReservationRepository
                .findByOrderIdAndStatusWithStockItem(orderId, ReservationStatus.RESERVED);
        if (!existing.isEmpty()) {
            log.info("Reservation already exists for order {} - returning existing reservation (idempotent)", orderId);
            List<ReservationItemResponse> existingItems = existing.stream()
                    .map(r -> new ReservationItemResponse(r.getId(), r.getProductId(),
                            r.getStockItem().getWarehouse().getId(), r.getQuantityReserved()))
                    .toList();
            return new StockReservationResponse(orderId, "RESERVED", existingItems, existing.getFirst().getExpiresAt());
        }

        List<ReservationItemResponse> reservationItems = new ArrayList<>();
        Instant now = Instant.now();

        for (StockCheckRequest req : items) {
            List<StockItem> stockItems = stockItemRepository.findByProductIdForUpdateOrderByAvailableDesc(req.productId());
            if (stockItems.isEmpty()) {
                throw new InsufficientStockException("No stock records found for product: " + req.productId());
            }

            int remaining = req.quantity();
            boolean anyBackorderable = stockItems.stream().anyMatch(StockItem::isBackorderable);
            int totalAvailable = stockItems.stream().mapToInt(StockItem::getQuantityAvailable).sum();

            if (totalAvailable < remaining && !anyBackorderable) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + req.productId() +
                        ": requested=" + req.quantity() + ", available=" + totalAvailable
                );
            }

            for (StockItem stockItem : stockItems) {
                if (remaining <= 0) break;

                int currentAvailable = stockItem.getQuantityAvailable();
                int allocate = Math.min(remaining, Math.max(0, currentAvailable));
                if (allocate == 0 && !stockItem.isBackorderable()) continue;
                if (allocate == 0 && stockItem.isBackorderable()) {
                    allocate = remaining;
                }

                int quantityBefore = stockItem.getQuantityAvailable();
                stockItem.setQuantityReserved(stockItem.getQuantityReserved() + allocate);
                stockItem.recalculateAvailable();

                if (stockItem.getQuantityAvailable() < 0 && !stockItem.isBackorderable()) {
                    throw new InsufficientStockException(
                            "Reservation would result in negative available stock for product "
                            + req.productId() + " in warehouse " + stockItem.getWarehouse().getId());
                }

                stockItem.recalculateStatus();
                stockItemRepository.save(stockItem);

                StockReservation reservation = StockReservation.builder()
                        .orderId(orderId)
                        .productId(req.productId())
                        .stockItem(stockItem)
                        .quantityReserved(allocate)
                        .status(ReservationStatus.RESERVED)
                        .reservedAt(now)
                        .expiresAt(expiresAt)
                        .build();
                stockReservationRepository.save(reservation);

                recordMovement(stockItem, MovementType.RESERVATION, -allocate,
                        quantityBefore, stockItem.getQuantityAvailable(),
                        "order", orderId, "system", "order-service",
                        "Reserved for order " + orderId);

                reservationItems.add(new ReservationItemResponse(
                        reservation.getId(), req.productId(),
                        stockItem.getWarehouse().getId(), allocate
                ));

                remaining -= allocate;
            }

            if (remaining > 0) {
                throw new InsufficientStockException(
                        "Could not fully reserve stock for product " + req.productId() +
                        ": shortfall=" + remaining
                );
            }
        }

        return new StockReservationResponse(orderId, "RESERVED", reservationItems, expiresAt);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void confirmReservation(UUID orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatusWithStockItemForUpdate(orderId, ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            boolean alreadyConfirmed = stockReservationRepository.existsByOrderIdAndStatusIn(
                    orderId,
                    EnumSet.of(ReservationStatus.CONFIRMED)
            );
            if (alreadyConfirmed) {
                log.info("Reservations for order {} are already confirmed. Skipping duplicate confirmation.", orderId);
                return;
            }
            throw new ResourceNotFoundException(
                    "No RESERVED reservations found for order " + orderId
                            + ". The reservation may have expired. A new order may be needed.");
        }

        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("StockItem not found: " + reservation.getStockItem().getId()));

            if (stockItem.getQuantityOnHand() < reservation.getQuantityReserved()) {
                log.error("Insufficient on-hand ({}) for reservation confirmation ({}). Stock item: {}, Reservation: {}, Order: {}",
                        stockItem.getQuantityOnHand(), reservation.getQuantityReserved(),
                        stockItem.getId(), reservation.getId(), orderId);
                throw new InsufficientStockException(
                        "On-hand quantity (" + stockItem.getQuantityOnHand()
                                + ") insufficient for confirmation of " + reservation.getQuantityReserved()
                                + " units. Stock item: " + stockItem.getId());
            }

            int quantityBefore = stockItem.getQuantityAvailable();
            stockItem.setQuantityOnHand(stockItem.getQuantityOnHand() - reservation.getQuantityReserved());
            stockItem.setQuantityReserved(stockItem.getQuantityReserved() - reservation.getQuantityReserved());
            stockItem.recalculateAvailable();
            stockItem.recalculateStatus();
            stockItemRepository.save(stockItem);

            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(now);
            stockReservationRepository.save(reservation);

            recordMovement(stockItem, MovementType.RESERVATION_CONFIRM, -reservation.getQuantityReserved(),
                    quantityBefore, stockItem.getQuantityAvailable(),
                    "order", orderId, "system", "payment-confirmation",
                    "Confirmed for order " + orderId);
        }

        log.info("Confirmed {} reservations for order {}", reservations.size(), orderId);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void releaseReservation(UUID orderId, String reason) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatusWithStockItemForUpdate(orderId, ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.warn("No RESERVED reservations found to release for order {}", orderId);
            return;
        }

        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("StockItem not found: " + reservation.getStockItem().getId()));

            int quantityBefore = stockItem.getQuantityAvailable();
            stockItem.setQuantityReserved(stockItem.getQuantityReserved() - reservation.getQuantityReserved());
            stockItem.recalculateAvailable();
            stockItem.recalculateStatus();
            stockItemRepository.save(stockItem);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setReleaseReason(reason);
            stockReservationRepository.save(reservation);

            recordMovement(stockItem, MovementType.RESERVATION_RELEASE, reservation.getQuantityReserved(),
                    quantityBefore, stockItem.getQuantityAvailable(),
                    "order", orderId, "system", "order-service",
                    "Released: " + reason);
        }

        log.info("Released {} reservations for order {} (reason: {})", reservations.size(), orderId, reason);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void cancelOrderReservations(UUID orderId, String reason) {
        List<StockReservation> reserved = stockReservationRepository
                .findByOrderIdAndStatusWithStockItemForUpdate(orderId, ReservationStatus.RESERVED);
        List<StockReservation> confirmed = stockReservationRepository
                .findByOrderIdAndStatusWithStockItemForUpdate(orderId, ReservationStatus.CONFIRMED);

        if (reserved.isEmpty() && confirmed.isEmpty()) {
            log.warn("No active reservations found to cancel for order {}", orderId);
            return;
        }

        Instant now = Instant.now();
        for (StockReservation reservation : reserved) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("StockItem not found: " + reservation.getStockItem().getId()));

            int quantityBefore = stockItem.getQuantityAvailable();
            stockItem.setQuantityReserved(stockItem.getQuantityReserved() - reservation.getQuantityReserved());
            stockItem.recalculateAvailable();
            stockItem.recalculateStatus();
            stockItemRepository.save(stockItem);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setReleaseReason(reason);
            stockReservationRepository.save(reservation);

            recordMovement(stockItem, MovementType.RESERVATION_RELEASE, reservation.getQuantityReserved(),
                    quantityBefore, stockItem.getQuantityAvailable(),
                    "order", orderId, "system", "order-cancellation",
                    "Released (RESERVED): " + reason);
        }

        for (StockReservation reservation : confirmed) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("StockItem not found: " + reservation.getStockItem().getId()));

            int quantityBefore = stockItem.getQuantityAvailable();
            stockItem.setQuantityOnHand(stockItem.getQuantityOnHand() + reservation.getQuantityReserved());
            stockItem.recalculateAvailable();
            stockItem.recalculateStatus();
            stockItemRepository.save(stockItem);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setReleaseReason("REVERSAL: " + reason);
            stockReservationRepository.save(reservation);

            recordMovement(stockItem, MovementType.RESERVATION_RELEASE, reservation.getQuantityReserved(),
                    quantityBefore, stockItem.getQuantityAvailable(),
                    "order", orderId, "system", "order-cancellation",
                    "Reversed (CONFIRMED): " + reason);
        }

        log.info("Cancelled {} reserved + {} confirmed reservations for order {} (reason: {})",
                reserved.size(), confirmed.size(), orderId, reason);
    }

    @Transactional(readOnly = true)
    public StockAvailabilitySummary getStockSummary(UUID productId) {
        CatalogProduct catalogProduct = catalogProductService.findByProductId(productId);
        List<StockItem> items = stockItemRepository.findByProductId(productId);
        return buildSummary(productId, catalogProduct, items);
    }

    @Transactional(readOnly = true)
    public List<StockAvailabilitySummary> getBatchStockSummary(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        List<StockItem> allItems = stockItemRepository.findByProductIdIn(productIds);
        Map<UUID, CatalogProduct> catalogProducts = catalogProductService.findByProductIds(productIds);
        Map<UUID, List<StockItem>> grouped = allItems.stream()
                .collect(Collectors.groupingBy(StockItem::getProductId));

        List<StockAvailabilitySummary> results = new ArrayList<>();
        for (UUID productId : productIds) {
            List<StockItem> items = grouped.getOrDefault(productId, List.of());
            results.add(buildSummary(productId, catalogProducts.get(productId), items));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> listStock(Pageable pageable, UUID vendorId, UUID productId, UUID warehouseId, StockStatus stockStatus) {
        return stockItemRepository.findFiltered(vendorId, productId, warehouseId, stockStatus, pageable)
                .map(this::toStockItemResponse);
    }

    @Transactional(readOnly = true)
    public StockItemResponse getStockItem(UUID id) {
        return toStockItemResponse(findStockItemById(id));
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public StockItemResponse createStockItem(StockItemCreateRequest request) {
        CatalogProduct catalogProduct = catalogProductService.requireOwnedProduct(request.productId(), request.vendorId());
        Warehouse warehouse = warehouseService.findById(request.warehouseId());
        assertWarehouseBelongsToVendor(warehouse, request.vendorId());

        stockItemRepository.findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
                .ifPresent(existing -> {
                    throw new ValidationException("Stock item already exists for product " + request.productId()
                            + " in warehouse " + request.warehouseId());
                });

        StockItem stockItem = StockItem.builder()
                .productId(request.productId())
                .vendorId(request.vendorId())
                .warehouse(warehouse)
                .sku(resolveStockSku(request.sku(), catalogProduct))
                .quantityOnHand(request.quantityOnHand())
                .quantityReserved(0)
                .quantityAvailable(request.quantityOnHand())
                .lowStockThreshold(request.lowStockThreshold())
                .backorderable(request.backorderable())
                .build();
        stockItem.recalculateStatus();

        stockItem = stockItemRepository.save(stockItem);

        if (request.quantityOnHand() > 0) {
            recordMovement(stockItem, MovementType.STOCK_IN, request.quantityOnHand(),
                    0, request.quantityOnHand(),
                    "adjustment", null, "admin", "stock-create",
                    "Initial stock creation");
        }

        return toStockItemResponse(stockItem);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public StockItemResponse updateStockItem(UUID id, StockItemUpdateRequest request) {
        StockItem stockItem = findStockItemById(id);
        CatalogProduct catalogProduct = catalogProductService.requireOwnedProduct(stockItem.getProductId(), stockItem.getVendorId());

        if (request.sku() != null) stockItem.setSku(resolveStockSku(request.sku(), catalogProduct));
        if (request.lowStockThreshold() != null) stockItem.setLowStockThreshold(request.lowStockThreshold());
        if (request.backorderable() != null) stockItem.setBackorderable(request.backorderable());

        stockItem.recalculateStatus();
        return toStockItemResponse(stockItemRepository.save(stockItem));
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public StockItemResponse adjustStock(UUID id, int quantityChange, String reason, String actorType, String actorId) {
        StockItem stockItem = stockItemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock item not found: " + id));

        int quantityBefore = stockItem.getQuantityAvailable();
        stockItem.setQuantityOnHand(stockItem.getQuantityOnHand() + quantityChange);
        stockItem.recalculateAvailable();
        stockItem.recalculateStatus();

        if (stockItem.getQuantityOnHand() < 0) {
            throw new ValidationException("Adjustment would result in negative on-hand quantity");
        }

        stockItem = stockItemRepository.save(stockItem);

        recordMovement(stockItem, MovementType.ADJUSTMENT, quantityChange,
                quantityBefore, stockItem.getQuantityAvailable(),
                "adjustment", null, actorType, actorId, reason);

        return toStockItemResponse(stockItem);
    }

    public BulkStockImportResponse bulkImport(List<StockItemCreateRequest> items, String actorType, String actorId) {
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
        txTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        txTemplate.setTimeout(10);

        for (int i = 0; i < items.size(); i++) {
            StockItemCreateRequest req = items.get(i);
            final int index = i;
            try {
                String result = txTemplate.execute(status -> {
                    CatalogProduct catalogProduct = catalogProductService.requireOwnedProduct(req.productId(), req.vendorId());
                    Optional<StockItem> existing = stockItemRepository.findByProductIdAndWarehouseId(req.productId(), req.warehouseId());
                    if (existing.isPresent()) {
                        StockItem stockItem = existing.get();
                        int quantityBefore = stockItem.getQuantityAvailable();
                        int diff = req.quantityOnHand() - stockItem.getQuantityOnHand();
                        stockItem.setQuantityOnHand(req.quantityOnHand());
                        stockItem.setSku(resolveStockSku(req.sku(), catalogProduct));
                        stockItem.setLowStockThreshold(req.lowStockThreshold());
                        stockItem.setBackorderable(req.backorderable());
                        stockItem.recalculateAvailable();
                        stockItem.recalculateStatus();
                        stockItemRepository.save(stockItem);

                        if (diff != 0) {
                            recordMovement(stockItem, MovementType.BULK_IMPORT, diff,
                                    quantityBefore, stockItem.getQuantityAvailable(),
                                    "bulk_import", null, actorType, actorId, "Bulk import update");
                        }
                        return "updated";
                    } else {
                        Warehouse warehouse = warehouseService.findById(req.warehouseId());
                        assertWarehouseBelongsToVendor(warehouse, req.vendorId());
                        StockItem stockItem = StockItem.builder()
                                .productId(req.productId())
                                .vendorId(req.vendorId())
                                .warehouse(warehouse)
                                .sku(resolveStockSku(req.sku(), catalogProduct))
                                .quantityOnHand(req.quantityOnHand())
                                .quantityReserved(0)
                                .quantityAvailable(req.quantityOnHand())
                                .lowStockThreshold(req.lowStockThreshold())
                                .backorderable(req.backorderable())
                                .build();
                        stockItem.recalculateStatus();
                        stockItem = stockItemRepository.save(stockItem);

                        if (req.quantityOnHand() > 0) {
                            recordMovement(stockItem, MovementType.BULK_IMPORT, req.quantityOnHand(),
                                    0, req.quantityOnHand(),
                                    "bulk_import", null, actorType, actorId, "Bulk import create");
                        }
                        return "created";
                    }
                });
                if ("created".equals(result)) created++;
                else updated++;
            } catch (Exception e) {
                errors.add("Item[" + index + "] productId=" + req.productId() + ": " + e.getMessage());
            }
        }

        return new BulkStockImportResponse(items.size(), created, updated, errors);
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> listLowStock(Pageable pageable) {
        return stockItemRepository.findLowStock(pageable).map(this::toStockItemResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockItemResponse> listLowStockByVendor(UUID vendorId, Pageable pageable) {
        return stockItemRepository.findLowStockByVendorId(vendorId, pageable).map(this::toStockItemResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(Pageable pageable, UUID vendorId, UUID productId, UUID warehouseId, MovementType movementType) {
        if (vendorId != null) {
            return stockMovementRepository.findFilteredByVendor(vendorId, productId, warehouseId, movementType, pageable)
                    .map(this::toMovementResponse);
        }
        return stockMovementRepository.findFiltered(productId, warehouseId, movementType, pageable)
                .map(this::toMovementResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovementsByVendor(UUID vendorId, Pageable pageable) {
        return stockMovementRepository.findByVendorId(vendorId, pageable)
                .map(this::toMovementResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockReservationDetailResponse> listReservations(Pageable pageable, UUID vendorId, ReservationStatus status, UUID orderId) {
        if (vendorId != null) {
            return stockReservationRepository.findFilteredByVendor(vendorId, status, orderId, pageable)
                    .map(this::toReservationDetailResponse);
        }
        return stockReservationRepository.findFiltered(status, orderId, pageable)
                .map(this::toReservationDetailResponse);
    }

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public int expireStaleReservationsBatch(int batchSize) {
        Instant now = Instant.now();
        Page<StockReservation> page = stockReservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, now, PageRequest.of(0, batchSize));

        int expired = 0;
        for (StockReservation candidate : page.getContent()) {
            StockReservation reservation = stockReservationRepository
                    .findByIdAndStatusWithStockItemForUpdate(candidate.getId(), ReservationStatus.RESERVED)
                    .orElse(null);
            if (reservation == null) {
                continue;
            }

            String orderStatus = lookupOrderStatus(reservation.getOrderId());
            if (shouldConfirmExpiredReservation(orderStatus)) {
                confirmReservation(reservation.getOrderId());
                continue;
            }
            if (!shouldReleaseExpiredReservation(orderStatus)) {
                log.warn("Skipping expiry for reservation {} on order {} because order status could not be safely verified (status={}).",
                        reservation.getId(), reservation.getOrderId(), orderStatusForLog(orderStatus));
                continue;
            }

            expireReservation(reservation, now);
            expired++;
        }

        if (expired > 0) {
            log.info("Expired {} stale reservations (batch)", expired);
        }
        return expired;
    }

    public StockItem findStockItemById(UUID id) {
        return stockItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock item not found: " + id));
    }

    private StockAvailabilitySummary buildSummary(UUID productId, CatalogProduct catalogProduct, List<StockItem> items) {
        if (catalogProduct == null || catalogProduct.isDeleted()) {
            return new StockAvailabilitySummary(productId, 0, 0, 0, false, StockStatus.OUT_OF_STOCK.name());
        }
        List<StockItem> filteredItems = items.stream()
                .filter(item -> catalogProduct.getVendorId().equals(item.getVendorId()))
                .toList();
        int totalAvailable = filteredItems.stream().mapToInt(StockItem::getQuantityAvailable).sum();
        int totalOnHand = filteredItems.stream().mapToInt(StockItem::getQuantityOnHand).sum();
        int totalReserved = filteredItems.stream().mapToInt(StockItem::getQuantityReserved).sum();
        boolean backorderable = filteredItems.stream().anyMatch(StockItem::isBackorderable);
        String status = resolveAggregateStatus(totalAvailable, backorderable, filteredItems);
        return new StockAvailabilitySummary(productId, totalAvailable, totalOnHand, totalReserved, backorderable, status);
    }

    private String resolveStockSku(String requestedSku, CatalogProduct catalogProduct) {
        String catalogSku = catalogProduct.getSku();
        if (requestedSku == null || requestedSku.isBlank()) {
            return catalogSku;
        }
        String normalizedRequestedSku = requestedSku.trim();
        if (!normalizedRequestedSku.equals(catalogSku)) {
            throw new ValidationException("Stock item SKU must match the product catalog SKU");
        }
        return normalizedRequestedSku;
    }

    private void assertWarehouseBelongsToVendor(Warehouse warehouse, UUID vendorId) {
        if (warehouse.getVendorId() != null && !warehouse.getVendorId().equals(vendorId)) {
            throw new ValidationException("Warehouse " + warehouse.getId()
                    + " does not belong to vendor " + vendorId);
        }
    }

    private String lookupOrderStatus(UUID orderId) {
        try {
            OrderStatusSnapshot snapshot = orderClient.getOrderStatus(orderId);
            return snapshot == null ? null : snapshot.status();
        } catch (ResourceNotFoundException | ServiceUnavailableException ex) {
            log.warn("Failed to verify order status before expiring reservation for order {}.", orderId, ex);
            return null;
        } catch (RuntimeException ex) {
            log.warn("Unexpected failure while verifying order status before expiring reservation for order {}.", orderId, ex);
            return null;
        }
    }

    private boolean shouldConfirmExpiredReservation(String orderStatus) {
        if (orderStatus == null) {
            return false;
        }
        return switch (orderStatus) {
            case "CONFIRMED", "ON_HOLD", "PROCESSING", "SHIPPED", "DELIVERED",
                    "RETURN_REQUESTED", "RETURN_REJECTED", "REFUND_PENDING", "REFUNDED", "CLOSED" -> true;
            default -> false;
        };
    }

    private boolean shouldReleaseExpiredReservation(String orderStatus) {
        if (orderStatus == null) {
            return false;
        }
        return switch (orderStatus) {
            case "PENDING", "PAYMENT_PENDING", "PAYMENT_FAILED", "CANCELLED" -> true;
            default -> false;
        };
    }

    private String orderStatusForLog(String orderStatus) {
        return orderStatus == null || orderStatus.isBlank() ? "UNKNOWN" : orderStatus;
    }

    private void expireReservation(StockReservation reservation, Instant now) {
        StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                .orElse(null);
        if (stockItem == null) {
            log.warn("StockItem {} not found during expiry of reservation {}", reservation.getStockItem().getId(), reservation.getId());
            return;
        }

        int quantityBefore = stockItem.getQuantityAvailable();
        stockItem.setQuantityReserved(stockItem.getQuantityReserved() - reservation.getQuantityReserved());
        stockItem.recalculateAvailable();
        stockItem.recalculateStatus();
        stockItemRepository.save(stockItem);

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservation.setReleasedAt(now);
        reservation.setReleaseReason("Reservation expired");
        stockReservationRepository.save(reservation);

        recordMovement(stockItem, MovementType.RESERVATION_RELEASE, reservation.getQuantityReserved(),
                quantityBefore, stockItem.getQuantityAvailable(),
                "order", reservation.getOrderId(), "system", "scheduler",
                "Reservation expired for order " + reservation.getOrderId());
    }

    private String resolveAggregateStatus(int totalAvailable, boolean backorderable, List<StockItem> items) {
        if (items.isEmpty()) return StockStatus.OUT_OF_STOCK.name();
        if (totalAvailable <= 0 && backorderable) return StockStatus.BACKORDER.name();
        if (totalAvailable <= 0) return StockStatus.OUT_OF_STOCK.name();
        boolean anyLow = items.stream().anyMatch(s -> s.getStockStatus() == StockStatus.LOW_STOCK);
        if (anyLow) return StockStatus.LOW_STOCK.name();
        return StockStatus.IN_STOCK.name();
    }

    private void recordMovement(StockItem stockItem, MovementType type, int quantityChange,
                                int quantityBefore, int quantityAfter,
                                String referenceType, UUID referenceId,
                                String actorType, String actorId, String note) {
        StockMovement movement = StockMovement.builder()
                .stockItem(stockItem)
                .productId(stockItem.getProductId())
                .warehouseId(stockItem.getWarehouse().getId())
                .movementType(type)
                .quantityChange(quantityChange)
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .actorType(actorType)
                .actorId(actorId)
                .note(note)
                .build();
        stockMovementRepository.save(movement);
    }

    private StockItemResponse toStockItemResponse(StockItem s) {
        return new StockItemResponse(
                s.getId(), s.getProductId(), s.getVendorId(),
                s.getWarehouse().getId(), s.getWarehouse().getName(),
                s.getSku(), s.getQuantityOnHand(), s.getQuantityReserved(),
                s.getQuantityAvailable(), s.getLowStockThreshold(),
                s.isBackorderable(), s.getStockStatus().name(),
                s.getCreatedAt(), s.getUpdatedAt()
        );
    }

    private StockMovementResponse toMovementResponse(StockMovement m) {
        return new StockMovementResponse(
                m.getId(), m.getStockItem().getId(), m.getProductId(),
                m.getWarehouseId(), m.getMovementType().name(),
                m.getQuantityChange(), m.getQuantityBefore(), m.getQuantityAfter(),
                m.getReferenceType(), m.getReferenceId(),
                m.getActorType(), m.getActorId(), m.getNote(), m.getCreatedAt()
        );
    }

    private StockReservationDetailResponse toReservationDetailResponse(StockReservation r) {
        return new StockReservationDetailResponse(
                r.getId(), r.getOrderId(), r.getProductId(),
                r.getStockItem().getId(), r.getStockItem().getWarehouse().getId(),
                r.getQuantityReserved(), r.getStatus().name(),
                r.getReservedAt(), r.getExpiresAt(), r.getConfirmedAt(),
                r.getReleasedAt(), r.getReleaseReason(), r.getCreatedAt()
        );
    }
}
