package com.rumal.order_service.controller;

import com.rumal.order_service.dto.VendorOrderDetailResponse;
import com.rumal.order_service.dto.VendorOrderResponse;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.exception.UnauthorizedException;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders/vendor/me")
@RequiredArgsConstructor
public class VendorOrderSelfServiceController {

    private final OrderService orderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<VendorOrderResponse> listMyVendorOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        verifyAuth(internalAuth, userSub);
        return orderService.listVendorOrdersForVendorUser(userSub, vendorId, status, pageable);
    }

    @GetMapping("/{vendorOrderId}")
    public VendorOrderDetailResponse getMyVendorOrder(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID vendorOrderId
    ) {
        verifyAuth(internalAuth, userSub);
        return orderService.getVendorOrderForVendorUser(userSub, vendorId, vendorOrderId);
    }

    private void verifyAuth(String internalAuth, String userSub) {
        internalRequestVerifier.verify(internalAuth);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
    }
}
