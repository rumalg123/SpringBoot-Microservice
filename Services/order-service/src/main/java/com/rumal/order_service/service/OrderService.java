package com.rumal.order_service.service;


import com.rumal.order_service.client.CustomerClient;
import com.rumal.order_service.config.CustomerDetailsMode;
import com.rumal.order_service.config.OrderAggregationProperties;
import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CustomerSummary;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final OrderAggregationProperties props;

    public OrderResponse create(CreateOrderRequest req) {
        customerClient.assertCustomerExists(req.customerId());

        Order saved = orderRepository.save(
                Order.builder()
                        .customerId(req.customerId())
                        .item(req.item().trim())
                        .quantity(req.quantity())
                        .build()
        );

        return toResponse(saved);
    }

    public OrderResponse get(UUID id) {
        Order o = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toResponse(o);
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getItem(), o.getQuantity(), o.getCreatedAt());
    }

    public Page<OrderResponse> list(UUID customerId, Pageable pageable) {
        Page<Order> page = (customerId == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByCustomerId(customerId, pageable);

        return page.map(this::toResponse);
    }

    public OrderDetailsResponse getDetails(UUID orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        CustomerSummary customer = null;
        List<String> warnings = new ArrayList<>();

        try {
            customer = customerClient.getCustomer(o.getCustomerId());
        } catch (ServiceUnavailableException ex) {
            if (props.customerDetailsMode() == CustomerDetailsMode.STRICT) {
                // STRICT = fail the whole request
                throw ex;
            }
            // GRACEFUL = keep going
            warnings.add("CUSTOMER_DETAILS_UNAVAILABLE");
        }

        return new OrderDetailsResponse(
                o.getId(),
                o.getCustomerId(),
                o.getItem(),
                o.getQuantity(),
                o.getCreatedAt(),
                customer,
                warnings
        );
    }

}
