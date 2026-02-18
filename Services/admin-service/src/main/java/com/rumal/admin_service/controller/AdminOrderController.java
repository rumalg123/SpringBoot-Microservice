package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public PageResponse<OrderResponse> listOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminOrderService.listOrders(customerId, pageable, internalAuth);
    }
}
