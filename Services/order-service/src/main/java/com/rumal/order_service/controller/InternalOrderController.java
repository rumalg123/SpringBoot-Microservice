package com.rumal.order_service.controller;

import com.rumal.order_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final OrderService orderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/vendors/{vendorId}/deletion-check")
    public VendorOrderDeletionCheckResponse getVendorDeletionCheck(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getVendorDeletionCheck(vendorId);
    }
}
