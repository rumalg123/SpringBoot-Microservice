package com.rumal.order_service.controller;

import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderResponse;
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
            @RequestHeader("X-Auth0-Sub") String auth0Id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return orderService.listForAuth0Id(auth0Id, pageable);
    }

    @GetMapping("/{id}/details")
    public OrderDetailsResponse details(@PathVariable UUID id) {
        return orderService.getDetails(id);
    }


}
