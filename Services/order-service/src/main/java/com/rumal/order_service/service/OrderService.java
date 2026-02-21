package com.rumal.order_service.service;


import com.rumal.order_service.client.CustomerClient;
import com.rumal.order_service.client.ProductClient;
import com.rumal.order_service.config.CustomerDetailsMode;
import com.rumal.order_service.config.OrderAggregationProperties;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CustomerAddressSummary;
import com.rumal.order_service.dto.CustomerSummary;
import com.rumal.order_service.dto.OrderAddressResponse;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderItemResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.dto.ProductSummary;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderAddressSnapshot;
import com.rumal.order_service.entity.OrderItem;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.exception.ValidationException;
import com.rumal.order_service.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderAggregationProperties props;

    @Caching(evict = {
            @CacheEvict(cacheNames = "ordersByKeycloak", allEntries = true),
            @CacheEvict(cacheNames = "orderDetailsByKeycloak", allEntries = true)
    })
    public OrderResponse create(CreateOrderRequest req) {
        customerClient.assertCustomerExists(req.customerId());
        ProductSummary product = resolvePurchasableProduct(req.productId());
        ResolvedOrderAddresses addresses = resolveOrderAddresses(req.customerId(), req.shippingAddressId(), req.billingAddressId());

        Order saved = orderRepository.save(buildOrder(req.customerId(), product, req.quantity(), addresses));

        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "ordersByKeycloak", allEntries = true),
            @CacheEvict(cacheNames = "orderDetailsByKeycloak", allEntries = true)
    })
    public OrderResponse createForKeycloak(String keycloakId, CreateMyOrderRequest req) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        ProductSummary product = resolvePurchasableProduct(req.productId());
        ResolvedOrderAddresses addresses = resolveOrderAddresses(customer.id(), req.shippingAddressId(), req.billingAddressId());

        Order saved = orderRepository.save(buildOrder(customer.id(), product, req.quantity(), addresses));

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
        return list(customerId, null, pageable);
    }

    public Page<OrderResponse> list(UUID customerId, String customerEmail, Pageable pageable) {
        UUID resolvedCustomerId = customerId;
        if (StringUtils.hasText(customerEmail)) {
            CustomerSummary customer = customerClient.getCustomerByEmail(customerEmail.trim());
            resolvedCustomerId = customer.id();
        }

        Page<Order> page = (resolvedCustomerId == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByCustomerId(resolvedCustomerId, pageable);

        return page.map(this::toResponse);
    }

    @Cacheable(
            cacheNames = "ordersByKeycloak",
            key = "#keycloakId + '::' + #pageable.pageNumber + '::' + #pageable.pageSize + '::' + #pageable.sort.toString()"
    )
    public Page<OrderResponse> listForKeycloakId(String keycloakId, Pageable pageable) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        return list(customer.id(), pageable);
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
                toItems(o),
                toAddressResponse(o.getShippingAddressId(), o.getShippingAddress()),
                toAddressResponse(o.getBillingAddressId(), o.getBillingAddress()),
                customer,
                warnings
        );
    }

    @Cacheable(cacheNames = "orderDetailsByKeycloak", key = "#keycloakId + '::' + #orderId")
    public OrderDetailsResponse getMyDetails(String keycloakId, UUID orderId) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!o.getCustomerId().equals(customer.id())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        return new OrderDetailsResponse(
                o.getId(),
                o.getCustomerId(),
                o.getItem(),
                o.getQuantity(),
                o.getCreatedAt(),
                toItems(o),
                toAddressResponse(o.getShippingAddressId(), o.getShippingAddress()),
                toAddressResponse(o.getBillingAddressId(), o.getBillingAddress()),
                customer,
                List.of()
        );
    }

    private Order buildOrder(UUID customerId, ProductSummary product, int quantity, ResolvedOrderAddresses addresses) {
        String normalizedItem = product.name().trim();
        Order order = Order.builder()
                .customerId(customerId)
                .item(normalizedItem)
                .quantity(quantity)
                .shippingAddressId(addresses.shippingAddress().id())
                .billingAddressId(addresses.billingAddress().id())
                .shippingAddress(toAddressSnapshot(addresses.shippingAddress()))
                .billingAddress(toAddressSnapshot(addresses.billingAddress()))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productId(product.id())
                .productSku(product.sku())
                .item(normalizedItem)
                .quantity(quantity)
                .build();
        order.getOrderItems().add(orderItem);
        return order;
    }

    private ResolvedOrderAddresses resolveOrderAddresses(UUID customerId, UUID shippingAddressId, UUID billingAddressId) {
        if (shippingAddressId == null) {
            throw new ValidationException("shippingAddressId is required");
        }
        if (billingAddressId == null) {
            throw new ValidationException("billingAddressId is required");
        }

        CustomerAddressSummary shippingAddress = customerClient.getCustomerAddress(customerId, shippingAddressId);
        CustomerAddressSummary billingAddress = customerClient.getCustomerAddress(customerId, billingAddressId);
        if (!customerId.equals(shippingAddress.customerId()) || !customerId.equals(billingAddress.customerId())) {
            throw new ValidationException("Selected addresses do not belong to the customer");
        }
        if (shippingAddress.deleted() || billingAddress.deleted()) {
            throw new ValidationException("Deleted addresses cannot be used for order placement");
        }
        return new ResolvedOrderAddresses(shippingAddress, billingAddress);
    }

    private OrderAddressSnapshot toAddressSnapshot(CustomerAddressSummary address) {
        return OrderAddressSnapshot.builder()
                .label(address.label())
                .recipientName(address.recipientName())
                .phone(address.phone())
                .line1(address.line1())
                .line2(address.line2())
                .city(address.city())
                .state(address.state())
                .postalCode(address.postalCode())
                .countryCode(address.countryCode())
                .build();
    }

    private OrderAddressResponse toAddressResponse(UUID sourceAddressId, OrderAddressSnapshot address) {
        if (address == null) {
            return null;
        }
        return new OrderAddressResponse(
                sourceAddressId,
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountryCode()
        );
    }

    private ProductSummary resolvePurchasableProduct(UUID productId) {
        ProductSummary product = productClient.getById(productId);
        if (!product.active()) {
            throw new ValidationException("Product is not active: " + productId);
        }
        if ("PARENT".equalsIgnoreCase(product.productType())) {
            throw new ValidationException("Parent products cannot be bought directly. Select a variation.");
        }
        return product;
    }

    private record ResolvedOrderAddresses(
            CustomerAddressSummary shippingAddress,
            CustomerAddressSummary billingAddress
    ) {
    }

    private List<OrderItemResponse> toItems(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return List.of(
                    new OrderItemResponse(null, order.getItem(), order.getQuantity())
            );
        }
        return order.getOrderItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getItem(), i.getQuantity()))
                .toList();
    }

}
