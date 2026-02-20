package com.rumal.order_service.controller;

import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.exception.UnauthorizedException;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest req) {
        return orderService.create(req);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }
    @GetMapping
    public Page<OrderResponse> list(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return orderService.list(customerId, pageable);
    }

    @GetMapping("/me")
    public Page<OrderResponse> listMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.listForKeycloakId(userSub, pageable);
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CreateMyOrderRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.createForKeycloak(userSub, req);
    }

    @GetMapping("/me/{id}")
    public OrderDetailsResponse detailsMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.getMyDetails(userSub, id);
    }

    @GetMapping("/{id}/details")
    public OrderDetailsResponse details(@PathVariable UUID id) {
        return orderService.getDetails(id);
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

}
