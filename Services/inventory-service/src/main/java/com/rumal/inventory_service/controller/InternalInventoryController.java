package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.*;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private final StockService stockService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/check")
    public List<StockCheckResult> checkAvailability(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody List<StockCheckRequest> requests
    ) {
        internalRequestVerifier.verify(internalAuth);
        return stockService.checkAvailability(requests);
    }

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public StockReservationResponse reserveStock(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody StockReserveRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return stockService.reserveForOrder(request.orderId(), request.items(), request.expiresAt());
    }

    @PostMapping("/reservations/{orderId}/confirm")
    public void confirmReservation(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID orderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        stockService.confirmReservation(orderId);
    }

    @PostMapping("/reservations/{orderId}/release")
    public void releaseReservation(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID orderId,
            @RequestBody(required = false) ReleaseRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        String reason = request != null && request.reason() != null ? request.reason() : "Released by order-service";
        stockService.releaseReservation(orderId, reason);
    }

    @PostMapping("/reservations/{orderId}/cancel")
    public void cancelOrderReservations(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID orderId,
            @RequestBody(required = false) ReleaseRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        String reason = request != null && request.reason() != null ? request.reason() : "Order cancelled";
        stockService.cancelOrderReservations(orderId, reason);
    }

    @GetMapping("/products/{productId}/stock-summary")
    public StockAvailabilitySummary getStockSummary(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID productId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return stockService.getStockSummary(productId);
    }

    @PostMapping("/products/stock-summary/batch")
    public List<StockAvailabilitySummary> getBatchStockSummary(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody BatchStockSummaryRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return stockService.getBatchStockSummary(request.productIds());
    }

    public record ReleaseRequest(String reason) {}
}
