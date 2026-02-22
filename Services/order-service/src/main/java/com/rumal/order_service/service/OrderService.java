package com.rumal.order_service.service;


import com.rumal.order_service.client.CustomerClient;
import com.rumal.order_service.client.ProductClient;
import com.rumal.order_service.dto.CreateOrderItemRequest;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderAggregationProperties props;

    @Caching(evict = {
            @CacheEvict(cacheNames = "ordersByKeycloak", allEntries = true),
            @CacheEvict(cacheNames = "orderDetailsByKeycloak", allEntries = true)
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public OrderResponse create(CreateOrderRequest req) {
        customerClient.assertCustomerExists(req.customerId());
        List<ResolvedOrderLine> lines = resolveOrderLines(req.productId(), req.quantity(), req.items());
        ResolvedOrderAddresses addresses = resolveOrderAddresses(req.customerId(), req.shippingAddressId(), req.billingAddressId());

        Order saved = orderRepository.save(buildOrder(req.customerId(), lines, addresses));

        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "ordersByKeycloak", allEntries = true),
            @CacheEvict(cacheNames = "orderDetailsByKeycloak", allEntries = true)
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public OrderResponse createForKeycloak(String keycloakId, CreateMyOrderRequest req) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        List<ResolvedOrderLine> lines = resolveOrderLines(req.productId(), req.quantity(), req.items());
        ResolvedOrderAddresses addresses = resolveOrderAddresses(customer.id(), req.shippingAddressId(), req.billingAddressId());

        Order saved = orderRepository.save(buildOrder(customer.id(), lines, addresses));

        return toResponse(saved);
    }

    public OrderResponse get(UUID id) {
        Order o = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toResponse(o);
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getCustomerId(),
                o.getItem(),
                o.getQuantity(),
                o.getItemCount(),
                normalizeMoney(o.getOrderTotal()),
                o.getCreatedAt()
        );
    }

    public Page<OrderResponse> list(UUID customerId, Pageable pageable) {
        return list(customerId, null, null, pageable);
    }

    public Page<OrderResponse> list(UUID customerId, String customerEmail, UUID vendorId, Pageable pageable) {
        UUID resolvedCustomerId = customerId;
        if (StringUtils.hasText(customerEmail)) {
            CustomerSummary customer = customerClient.getCustomerByEmail(customerEmail.trim());
            resolvedCustomerId = customer.id();
        }

        Page<Order> page;
        if (resolvedCustomerId != null && vendorId != null) {
            page = orderRepository.findByCustomerIdAndVendorId(resolvedCustomerId, vendorId, pageable);
        } else if (resolvedCustomerId != null) {
            page = orderRepository.findByCustomerId(resolvedCustomerId, pageable);
        } else if (vendorId != null) {
            page = orderRepository.findByVendorId(vendorId, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }

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
                o.getItemCount(),
                normalizeMoney(o.getOrderTotal()),
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
                o.getItemCount(),
                normalizeMoney(o.getOrderTotal()),
                o.getCreatedAt(),
                toItems(o),
                toAddressResponse(o.getShippingAddressId(), o.getShippingAddress()),
                toAddressResponse(o.getBillingAddressId(), o.getBillingAddress()),
                customer,
                List.of()
        );
    }

    private Order buildOrder(UUID customerId, List<ResolvedOrderLine> lines, ResolvedOrderAddresses addresses) {
        int totalQuantity = lines.stream()
                .mapToInt(ResolvedOrderLine::quantity)
                .sum();
        BigDecimal orderTotal = normalizeMoney(lines.stream()
                .map(ResolvedOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        int itemCount = lines.size();
        String summaryItem = itemCount == 1
                ? lines.getFirst().product().name().trim()
                : "Multiple items";

        Order order = Order.builder()
                .customerId(customerId)
                .item(summaryItem)
                .quantity(totalQuantity)
                .itemCount(itemCount)
                .orderTotal(orderTotal)
                .shippingAddressId(addresses.shippingAddress().id())
                .billingAddressId(addresses.billingAddress().id())
                .shippingAddress(toAddressSnapshot(addresses.shippingAddress()))
                .billingAddress(toAddressSnapshot(addresses.billingAddress()))
                .build();

        for (ResolvedOrderLine line : lines) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(line.product().id())
                    .vendorId(Objects.requireNonNull(line.product().vendorId(), "vendorId is required"))
                    .productSku(line.product().sku())
                    .item(line.product().name().trim())
                    .quantity(line.quantity())
                    .unitPrice(normalizeMoney(line.unitPrice()))
                    .lineTotal(normalizeMoney(line.lineTotal()))
                    .build();
            order.getOrderItems().add(orderItem);
        }
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
        if (product.sellingPrice() == null || product.sellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Product has invalid selling price: " + productId);
        }
        if (product.vendorId() == null) {
            throw new ValidationException("Product vendorId is missing: " + productId);
        }
        return product;
    }

    private List<ResolvedOrderLine> resolveOrderLines(
            UUID productId,
            Integer quantity,
            List<CreateOrderItemRequest> items
    ) {
        Map<UUID, Integer> normalized = new LinkedHashMap<>();
        boolean hasItems = items != null && !items.isEmpty();

        if (hasItems) {
            if (productId != null || quantity != null) {
                throw new ValidationException("Use either items[] or productId/quantity, not both");
            }
            for (CreateOrderItemRequest item : items) {
                if (item == null || item.productId() == null) {
                    throw new ValidationException("Each order item must include productId");
                }
                if (item.quantity() < 1) {
                    throw new ValidationException("Each order item quantity must be at least 1");
                }
                normalized.merge(item.productId(), item.quantity(), Integer::sum);
            }
        } else {
            if (productId == null) {
                throw new ValidationException("productId is required when items[] is empty");
            }
            int normalizedQuantity = quantity == null ? 0 : quantity;
            if (normalizedQuantity < 1) {
                throw new ValidationException("quantity must be at least 1");
            }
            normalized.put(productId, normalizedQuantity);
        }

        return normalized.entrySet().stream()
                .map(entry -> {
                    ProductSummary product = resolvePurchasableProduct(entry.getKey());
                    BigDecimal unitPrice = normalizeMoney(product.sellingPrice());
                    BigDecimal lineTotal = normalizeMoney(unitPrice.multiply(BigDecimal.valueOf(entry.getValue())));
                    return new ResolvedOrderLine(product, entry.getValue(), unitPrice, lineTotal);
                })
                .toList();
    }

    private record ResolvedOrderAddresses(
            CustomerAddressSummary shippingAddress,
            CustomerAddressSummary billingAddress
    ) {
    }

    private record ResolvedOrderLine(
            ProductSummary product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }

    private List<OrderItemResponse> toItems(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return List.of(
                    new OrderItemResponse(
                            null,
                            null,
                            null,
                            null,
                            order.getItem(),
                            order.getQuantity(),
                            null,
                            normalizeMoney(order.getOrderTotal())
                    )
            );
        }
        return order.getOrderItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getVendorId(),
                        i.getProductSku(),
                        i.getItem(),
                        i.getQuantity(),
                        normalizeMoney(i.getUnitPrice()),
                        normalizeMoney(i.getLineTotal())
                ))
                .toList();
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

}
