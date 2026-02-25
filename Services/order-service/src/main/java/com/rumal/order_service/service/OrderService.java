package com.rumal.order_service.service;


import com.rumal.order_service.client.CustomerClient;
import com.rumal.order_service.client.InventoryClient;
import com.rumal.order_service.client.ProductClient;
import com.rumal.order_service.client.PromotionClient;
import com.rumal.order_service.client.VendorClient;
import com.rumal.order_service.client.VendorOperationalStateClient;
import com.rumal.order_service.dto.StockCheckRequest;
import com.rumal.order_service.dto.CreateOrderItemRequest;
import com.rumal.order_service.dto.InvoiceResponse;
import com.rumal.order_service.dto.UpdateShippingAddressRequest;
import com.rumal.order_service.dto.VendorOrderDetailResponse;
import com.rumal.order_service.dto.VendorSummaryForOrder;
import com.rumal.order_service.config.CustomerDetailsMode;
import com.rumal.order_service.config.OrderAggregationProperties;
import com.rumal.order_service.dto.CustomerProductPurchaseCheckResponse;
import com.rumal.order_service.dto.CouponReservationResponse;
import com.rumal.order_service.dto.CancelOrderRequest;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CustomerAddressSummary;
import com.rumal.order_service.dto.CustomerSummary;
import com.rumal.order_service.dto.OrderAddressResponse;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderItemResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.dto.OrderStatusAuditResponse;
import com.rumal.order_service.dto.ProductSummary;
import com.rumal.order_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.order_service.dto.SetPaymentInfoRequest;
import com.rumal.order_service.dto.SetTrackingInfoRequest;
import com.rumal.order_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.order_service.dto.UpdateOrderNoteRequest;
import com.rumal.order_service.dto.VendorOrderResponse;
import com.rumal.order_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.order_service.dto.VendorOperationalStateResponse;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderAddressSnapshot;
import com.rumal.order_service.entity.OrderItem;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.entity.OrderStatusAudit;
import com.rumal.order_service.entity.VendorOrder;
import com.rumal.order_service.entity.VendorOrderStatusAudit;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.exception.ValidationException;
import com.rumal.order_service.repo.OrderRepository;
import com.rumal.order_service.repo.OrderSpecifications;
import com.rumal.order_service.repo.OrderStatusAuditRepository;
import com.rumal.order_service.repo.VendorOrderRepository;
import com.rumal.order_service.repo.VendorOrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_ITEM_QUANTITY = 1000;
    private static final int MAX_DISTINCT_ITEMS = 200;
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CONFIRMED
    );

    private static final Set<OrderStatus> VENDOR_DELETION_BLOCKING_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.ON_HOLD,
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.RETURN_REQUESTED,
            OrderStatus.RETURN_REJECTED,
            OrderStatus.REFUND_PENDING
    );

    private static final Set<OrderStatus> SHIPPING_ADDRESS_EDITABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.CONFIRMED
    );

    private final OrderRepository orderRepository;
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final VendorOrderRepository vendorOrderRepository;
    private final VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final PromotionClient promotionClient;
    private final InventoryClient inventoryClient;
    private final VendorClient vendorClient;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final ShippingFeeCalculator shippingFeeCalculator;
    private final OrderAggregationProperties props;
    private final OrderCacheVersionService orderCacheVersionService;
    private final TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Value("${order.expiry.ttl:30m}")
    private java.time.Duration orderExpiryTtl;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse create(CreateOrderRequest req) {
        customerClient.assertCustomerExists(req.customerId());
        List<ResolvedOrderLine> lines = resolveOrderLines(req.productId(), req.quantity(), req.items());
        assertVendorsAcceptingOrders(lines);
        ResolvedOrderAddresses addresses = resolveOrderAddresses(req.customerId(), req.shippingAddressId(), req.billingAddressId());
        return transactionTemplate.execute(status -> {
            Order saved = orderRepository.save(buildOrder(req.customerId(), lines, addresses, null, req.customerNote()));
            recordStatusAudit(saved, null, OrderStatus.PENDING, null, null, "system", "order_create", "Order created");
            saved.getVendorOrders().forEach(vendorOrder ->
                    recordVendorOrderStatusAudit(vendorOrder, null, OrderStatus.PENDING, null, null, "system", "order_create", "Vendor order created")
            );
            evictOrdersListCaches();
            return toResponse(saved);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse createForKeycloak(String keycloakId, CreateMyOrderRequest req) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        List<ResolvedOrderLine> lines = resolveOrderLines(req.productId(), req.quantity(), req.items());
        assertVendorsAcceptingOrders(lines);
        ResolvedOrderAddresses addresses = resolveOrderAddresses(customer.id(), req.shippingAddressId(), req.billingAddressId());
        PromotionPricingSnapshot pricingSnapshot = toPromotionPricingSnapshot(req.promotionPricing());
        Order saved = transactionTemplate.execute(status -> {
            Order order = orderRepository.save(buildOrder(customer.id(), lines, addresses, pricingSnapshot, req.customerNote()));
            recordStatusAudit(order, null, OrderStatus.PENDING, keycloakId, "customer", "customer", "order_create", "Customer order created");
            order.getVendorOrders().forEach(vendorOrder ->
                    recordVendorOrderStatusAudit(vendorOrder, null, OrderStatus.PENDING, keycloakId, "customer", "customer", "order_create", "Vendor order created")
            );
            evictOrdersListCaches();
            return order;
        });
        try {
            maybeCommitCouponReservation(saved, customer.id(), pricingSnapshot);
        } catch (RuntimeException ex) {
            log.warn("Coupon reservation commit failed after order {} was persisted. " +
                    "Order is valid but coupon state may need reconciliation. reservationId={}",
                    saved.getId(),
                    pricingSnapshot == null ? null : pricingSnapshot.couponReservationId(),
                    ex);
        }
        try {
            maybeReserveInventory(saved, lines);
        } catch (RuntimeException ex) {
            log.warn("Inventory reservation failed after order {} was persisted. " +
                    "Order exists in PENDING, expiry scheduler will clean up.",
                    saved.getId(), ex);
        }
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
                o.getCurrency(),
                normalizeMoney(o.getSubtotal()),
                normalizeMoney(o.getLineDiscountTotal()),
                normalizeMoney(o.getCartDiscountTotal()),
                normalizeMoney(o.getShippingAmount()),
                normalizeMoney(o.getShippingDiscountTotal()),
                normalizeMoney(o.getTotalDiscount()),
                o.getCouponCode(),
                o.getCouponReservationId(),
                o.getPaymentId(),
                o.getPaymentMethod(),
                o.getPaymentGatewayRef(),
                o.getPaidAt(),
                o.getCustomerNote(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getExpiresAt(),
                normalizeMoney(o.getRefundAmount()),
                o.getRefundReason(),
                o.getRefundInitiatedAt(),
                o.getRefundCompletedAt(),
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }

    public Page<OrderResponse> list(UUID customerId, Pageable pageable) {
        return list(customerId, null, null, null, null, null, pageable);
    }

    public Page<OrderResponse> list(
            UUID customerId, String customerEmail, UUID vendorId,
            OrderStatus status, Instant createdAfter, Instant createdBefore,
            Pageable pageable
    ) {
        UUID resolvedCustomerId = customerId;
        if (StringUtils.hasText(customerEmail)) {
            CustomerSummary customer = customerClient.getCustomerByEmail(customerEmail.trim());
            resolvedCustomerId = customer.id();
        }

        Page<Order> page = orderRepository.findAll(
                OrderSpecifications.withFilters(resolvedCustomerId, vendorId, status, createdAfter, createdBefore),
                pageable
        );

        return page.map(this::toResponse);
    }

    @Cacheable(
            cacheNames = "ordersByKeycloak",
            key = "@orderCacheVersionService.ordersByKeycloakVersion() + '::' + #keycloakId + '::' + "
                    + "(#status == null ? 'ALL' : #status.name()) + '::' + "
                    + "(#createdAfter == null ? 'NO_AFTER' : #createdAfter.toString()) + '::' + "
                    + "(#createdBefore == null ? 'NO_BEFORE' : #createdBefore.toString()) + '::' + "
                    + "#pageable.pageNumber + '::' + #pageable.pageSize + '::' + #pageable.sort.toString()"
    )
    public Page<OrderResponse> listForKeycloakId(
            String keycloakId, OrderStatus status, Instant createdAfter, Instant createdBefore, Pageable pageable
    ) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        return list(customer.id(), null, null, status, createdAfter, createdBefore, pageable);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public OrderResponse updateStatus(UUID orderId, OrderStatus status, String reason, String refundReason, BigDecimal refundAmount, Integer refundedQuantity, String actorSub, String actorRoles) {
        if (status == null) {
            throw new ValidationException("status is required");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getVendorOrders() != null && !order.getVendorOrders().isEmpty()) {
            if (order.getVendorOrders().size() > 1) {
                throw new ValidationException("Use vendor-order status updates for multi-vendor orders");
            }
            UUID vendorOrderId = order.getVendorOrders().getFirst().getId();
            updateVendorOrderStatus(vendorOrderId, status, reason, refundReason, refundAmount, refundedQuantity, actorSub, actorRoles);
            return toResponse(orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId)));
        }
        OrderStatus current = order.getStatus();
        validateStatusTransition(current, status);
        if (current == status) {
            return toResponse(order);
        }
        applyRefundFieldsIfNeeded(order, status, refundReason, refundAmount);
        order.setStatus(status);
        Order saved = orderRepository.save(order);
        String auditNote = StringUtils.hasText(reason) ? reason.trim() : "Order status updated";
        recordStatusAudit(
                saved,
                current,
                status,
                actorSub,
                actorRoles,
                StringUtils.hasText(actorSub) ? "admin_user" : "system",
                "status_update",
                auditNote
        );
        final Order savedOrder = saved;
        final OrderStatus finalStatus = status;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                maybeReleaseCouponReservationForFinalStatus(savedOrder, finalStatus);
                maybeHandleInventoryForStatusTransition(savedOrder, finalStatus);
            }
        });
        evictOrderCachesAfterStatusMutation();
        return toResponse(saved);
    }

    public List<OrderStatusAuditResponse> getStatusHistory(UUID orderId) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return orderStatusAuditRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toStatusAuditResponse)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public VendorOrderResponse updateVendorOrderStatus(UUID vendorOrderId, OrderStatus status, String reason, String refundReason, BigDecimal refundAmount, Integer refundedQuantity, String actorSub, String actorRoles) {
        if (status == null) {
            throw new ValidationException("status is required");
        }
        VendorOrder vendorOrder = vendorOrderRepository.findByIdForUpdate(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor order not found: " + vendorOrderId));
        OrderStatus current = vendorOrder.getStatus();
        validateStatusTransition(current, status);
        if (current == status) {
            return toVendorOrderResponse(vendorOrder);
        }

        applyRefundFieldsIfNeeded(vendorOrder, status, refundReason, refundAmount);
        if (refundedQuantity != null && refundedQuantity > 0 && status == OrderStatus.REFUNDED) {
            Integer previousQty = vendorOrder.getRefundedQuantity() == null ? 0 : vendorOrder.getRefundedQuantity();
            vendorOrder.setRefundedQuantity(previousQty + refundedQuantity);
        }
        vendorOrder.setStatus(status);
        VendorOrder savedVendorOrder = vendorOrderRepository.save(vendorOrder);
        String vendorAuditNote = StringUtils.hasText(reason) ? reason.trim() : "Vendor order status updated";
        recordVendorOrderStatusAudit(
                savedVendorOrder,
                current,
                status,
                actorSub,
                actorRoles,
                StringUtils.hasText(actorSub) ? "admin_user" : "system",
                "vendor_order_status_update",
                vendorAuditNote
        );

        Order parent = orderRepository.findByIdForUpdate(savedVendorOrder.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent order not found for vendor order: " + vendorOrderId));
        OrderStatus previousAggregate = parent.getStatus();
        OrderStatus nextAggregate = deriveAggregateOrderStatus(parent);
        if (previousAggregate != nextAggregate) {
            parent.setStatus(nextAggregate);
            Order savedOrder = orderRepository.save(parent);
            recordStatusAudit(
                    savedOrder,
                    previousAggregate,
                    nextAggregate,
                    actorSub,
                    actorRoles,
                    StringUtils.hasText(actorSub) ? "admin_user" : "system",
                    "vendor_order_aggregate_sync",
                    "Order aggregate status synchronized from vendor order statuses"
            );
            final Order finalSavedOrder = savedOrder;
            final OrderStatus finalNextAggregate = nextAggregate;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    maybeReleaseCouponReservationForFinalStatus(finalSavedOrder, finalNextAggregate);
                    maybeHandleInventoryForStatusTransition(finalSavedOrder, finalNextAggregate);
                }
            });
        }
        evictOrderCachesAfterStatusMutation();
        return toVendorOrderResponse(savedVendorOrder);
    }

    public List<VendorOrderResponse> getVendorOrders(UUID orderId) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return vendorOrderRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(this::toVendorOrderResponse)
                .toList();
    }

    public VendorOrderResponse getVendorOrder(UUID vendorOrderId) {
        VendorOrder vendorOrder = vendorOrderRepository.findById(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor order not found: " + vendorOrderId));
        return toVendorOrderResponse(vendorOrder);
    }

    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(UUID vendorOrderId) {
        vendorOrderRepository.findById(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor order not found: " + vendorOrderId));
        return vendorOrderStatusAuditRepository.findByVendorOrderIdOrderByCreatedAtDesc(vendorOrderId).stream()
                .map(this::toVendorOrderStatusAuditResponse)
                .toList();
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

        return toDetailsResponse(o, customer, warnings);
    }

    @Cacheable(
            cacheNames = "orderDetailsByKeycloak",
            key = "@orderCacheVersionService.orderDetailsByKeycloakVersion() + '::' + #keycloakId + '::' + #orderId"
    )
    public OrderDetailsResponse getMyDetails(String keycloakId, UUID orderId) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!o.getCustomerId().equals(customer.id())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        return toDetailsResponse(o, customer, List.of());
    }

    private OrderDetailsResponse toDetailsResponse(Order o, CustomerSummary customer, List<String> warnings) {
        return new OrderDetailsResponse(
                o.getId(),
                o.getCustomerId(),
                o.getItem(),
                o.getQuantity(),
                o.getItemCount(),
                normalizeMoney(o.getOrderTotal()),
                o.getCurrency(),
                normalizeMoney(o.getSubtotal()),
                normalizeMoney(o.getLineDiscountTotal()),
                normalizeMoney(o.getCartDiscountTotal()),
                normalizeMoney(o.getShippingAmount()),
                normalizeMoney(o.getShippingDiscountTotal()),
                normalizeMoney(o.getTotalDiscount()),
                o.getCouponCode(),
                o.getCouponReservationId(),
                o.getPaymentId(),
                o.getPaymentMethod(),
                o.getPaymentGatewayRef(),
                o.getPaidAt(),
                o.getCustomerNote(),
                o.getAdminNote(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getExpiresAt(),
                normalizeMoney(o.getRefundAmount()),
                o.getRefundReason(),
                o.getRefundInitiatedAt(),
                o.getRefundCompletedAt(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                toItems(o),
                toAddressResponse(o.getShippingAddressId(), o.getShippingAddress()),
                toAddressResponse(o.getBillingAddressId(), o.getBillingAddress()),
                customer,
                warnings
        );
    }

    private Order buildOrder(
            UUID customerId,
            List<ResolvedOrderLine> lines,
            ResolvedOrderAddresses addresses,
            PromotionPricingSnapshot requestedPricing,
            String customerNote
    ) {
        int totalQuantity = lines.stream()
                .mapToInt(ResolvedOrderLine::quantity)
                .sum();
        BigDecimal itemSubtotal = normalizeMoney(lines.stream()
                .map(ResolvedOrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal computedShippingAmount = calculateShippingForOrder(lines, addresses.shippingAddress().countryCode());
        PromotionPricingSnapshot pricing = validateAndResolvePricingSnapshot(requestedPricing, itemSubtotal, computedShippingAmount);
        BigDecimal orderTotal = pricing.grandTotal();
        if (orderTotal == null || orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Order grandTotal must be positive");
        }
        int itemCount = lines.size();
        String summaryItem;
        if (itemCount == 1) {
            String name = lines.getFirst().product().name().trim();
            summaryItem = name.length() > 120 ? name.substring(0, 120) : name;
        } else {
            String suffix = " + " + (itemCount - 1) + " more item" + (itemCount - 1 > 1 ? "s" : "");
            String firstName = lines.getFirst().product().name().trim();
            int maxNameLen = 120 - suffix.length();
            if (firstName.length() > maxNameLen) {
                firstName = firstName.substring(0, maxNameLen);
            }
            summaryItem = firstName + suffix;
        }

        Order order = Order.builder()
                .customerId(customerId)
                .item(summaryItem)
                .quantity(totalQuantity)
                .itemCount(itemCount)
                .subtotal(pricing.subtotal())
                .lineDiscountTotal(pricing.lineDiscountTotal())
                .cartDiscountTotal(pricing.cartDiscountTotal())
                .shippingAmount(pricing.shippingAmount())
                .shippingDiscountTotal(pricing.shippingDiscountTotal())
                .totalDiscount(pricing.totalDiscount())
                .couponCode(pricing.couponCode())
                .couponReservationId(pricing.couponReservationId())
                .orderTotal(orderTotal)
                .status(OrderStatus.PENDING)
                .shippingAddressId(addresses.shippingAddress().id())
                .billingAddressId(addresses.billingAddress().id())
                .shippingAddress(toAddressSnapshot(addresses.shippingAddress()))
                .billingAddress(toAddressSnapshot(addresses.billingAddress()))
                .customerNote(customerNote)
                .expiresAt(Instant.now().plus(orderExpiryTtl))
                .build();

        Map<UUID, List<ResolvedOrderLine>> linesByVendor = new LinkedHashMap<>();
        for (ResolvedOrderLine line : lines) {
            UUID vendorId = Objects.requireNonNull(line.product().vendorId(), "vendorId is required");
            linesByVendor.computeIfAbsent(vendorId, k -> new ArrayList<>()).add(line);
        }

        Map<UUID, VendorOrder> vendorOrdersByVendorId = new LinkedHashMap<>();
        BigDecimal totalSubtotal = pricing.subtotal();
        int vendorCount = linesByVendor.size();
        BigDecimal remainingDiscount = pricing.totalDiscount();
        BigDecimal remainingShipping = pricing.shippingAmount();
        int vendorIndex = 0;

        for (Map.Entry<UUID, List<ResolvedOrderLine>> entry : linesByVendor.entrySet()) {
            UUID vendorId = entry.getKey();
            List<ResolvedOrderLine> vendorLines = entry.getValue();
            int vendorQuantity = vendorLines.stream().mapToInt(ResolvedOrderLine::quantity).sum();
            int vendorItemCount = vendorLines.size();
            BigDecimal vendorSubtotal = normalizeMoney(vendorLines.stream()
                    .map(ResolvedOrderLine::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            vendorIndex++;
            boolean isLast = vendorIndex == vendorCount;

            // Distribute discount proportionally by subtotal ratio; last vendor gets remainder
            BigDecimal vendorDiscount;
            if (isLast) {
                vendorDiscount = normalizeMoney(remainingDiscount);
            } else if (totalSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                vendorDiscount = normalizeMoney(pricing.totalDiscount().multiply(vendorSubtotal)
                        .divide(totalSubtotal, 2, RoundingMode.HALF_UP));
                remainingDiscount = remainingDiscount.subtract(vendorDiscount);
            } else {
                vendorDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            // Distribute shipping evenly per vendor; last vendor gets remainder
            BigDecimal vendorShipping;
            if (isLast) {
                vendorShipping = normalizeMoney(remainingShipping);
            } else {
                vendorShipping = normalizeMoney(pricing.shippingAmount()
                        .divide(BigDecimal.valueOf(vendorCount), 2, RoundingMode.HALF_UP));
                remainingShipping = remainingShipping.subtract(vendorShipping);
            }

            BigDecimal vendorTotal = normalizeMoney(vendorSubtotal.subtract(vendorDiscount).add(vendorShipping));
            BigDecimal vendorPlatformFee = normalizeMoney(vendorTotal.multiply(PLATFORM_FEE_RATE));
            BigDecimal vendorPayout = normalizeMoney(vendorTotal.subtract(vendorPlatformFee));

            VendorOrder vendorOrder = VendorOrder.builder()
                    .order(order)
                    .vendorId(vendorId)
                    .status(OrderStatus.PENDING)
                    .itemCount(vendorItemCount)
                    .quantity(vendorQuantity)
                    .orderTotal(vendorTotal)
                    .discountAmount(vendorDiscount)
                    .shippingAmount(vendorShipping)
                    .platformFee(vendorPlatformFee)
                    .payoutAmount(vendorPayout)
                    .build();
            order.getVendorOrders().add(vendorOrder);
            vendorOrdersByVendorId.put(vendorId, vendorOrder);
        }

        for (ResolvedOrderLine line : lines) {
            UUID vendorId = Objects.requireNonNull(line.product().vendorId(), "vendorId is required");
            VendorOrder vendorOrder = Objects.requireNonNull(vendorOrdersByVendorId.get(vendorId), "vendorOrder is required");
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .vendorOrder(vendorOrder)
                    .productId(line.product().id())
                    .vendorId(vendorId)
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

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public OrderResponse updateAdminNote(UUID orderId, UpdateOrderNoteRequest req) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        order.setAdminNote(req.adminNote());
        Order saved = orderRepository.save(order);
        evictOrderCachesAfterStatusMutation();
        return toResponse(saved);
    }

    public List<OrderStatusAuditResponse> getMyStatusHistory(String keycloakId, UUID orderId) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getCustomerId().equals(customer.id())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return orderStatusAuditRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toStatusAuditResponse)
                .toList();
    }

    private void evictOrdersListCaches() {
        orderCacheVersionService.bumpOrdersByKeycloakCache();
    }

    private void evictOrderCachesAfterStatusMutation() {
        evictOrdersListCaches();
        orderCacheVersionService.bumpOrderDetailsByKeycloakCache();
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
        if (!StringUtils.hasText(address.line1())) {
            throw new ValidationException("Address line1 is required for order placement");
        }
        if (!StringUtils.hasText(address.city())) {
            throw new ValidationException("Address city is required for order placement");
        }
        if (!StringUtils.hasText(address.countryCode())) {
            throw new ValidationException("Address countryCode is required for order placement");
        }
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
                int requestedQuantity = validateOrderItemQuantity(item.quantity(), item.productId());
                normalized.merge(item.productId(), requestedQuantity, this::mergeOrderItemQuantities);
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

        if (normalized.size() > MAX_DISTINCT_ITEMS) {
            throw new ValidationException("Order cannot contain more than " + MAX_DISTINCT_ITEMS + " distinct items");
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

    private void assertVendorsAcceptingOrders(List<ResolvedOrderLine> lines) {
        Set<UUID> vendorIds = lines.stream()
                .map(line -> line.product().vendorId())
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (vendorIds.isEmpty()) {
            throw new ValidationException("No vendorId found for order items");
        }

        Map<UUID, VendorOperationalStateResponse> states = vendorOperationalStateClient.batchGetStates(vendorIds);
        for (UUID vendorId : vendorIds) {
            VendorOperationalStateResponse state = states.get(vendorId);
            if (state == null) {
                throw new ValidationException("Vendor is unavailable for ordering: " + vendorId);
            }
            boolean visible = !state.deleted() && state.active()
                    && "ACTIVE".equalsIgnoreCase(String.valueOf(state.status()))
                    && state.acceptingOrders()
                    && state.storefrontVisible();
            if (!visible) {
                throw new ValidationException("Vendor is not accepting orders: " + vendorId);
            }
        }
    }

    private int validateOrderItemQuantity(Integer quantity, UUID productId) {
        if (quantity == null) {
            throw new ValidationException("Each order item must include quantity");
        }
        if (quantity < 1) {
            throw new ValidationException("quantity must be at least 1 for product: " + productId);
        }
        if (quantity > MAX_ITEM_QUANTITY) {
            throw new ValidationException("quantity must be " + MAX_ITEM_QUANTITY + " or less for product: " + productId);
        }
        return quantity;
    }

    private int mergeOrderItemQuantities(int current, int incoming) {
        long merged = (long) current + incoming;
        if (merged > MAX_ITEM_QUANTITY) {
            throw new ValidationException("Combined quantity for the same product must be " + MAX_ITEM_QUANTITY + " or less");
        }
        return (int) merged;
    }

    private PromotionPricingSnapshot toPromotionPricingSnapshot(PromotionCheckoutPricingRequest request) {
        if (request == null) {
            return null;
        }
        BigDecimal subtotal = normalizeMoney(request.subtotal());
        BigDecimal lineDiscountTotal = normalizeMoney(request.lineDiscountTotal());
        BigDecimal cartDiscountTotal = normalizeMoney(request.cartDiscountTotal());
        BigDecimal shippingAmount = normalizeMoney(request.shippingAmount());
        BigDecimal shippingDiscountTotal = normalizeMoney(request.shippingDiscountTotal());
        BigDecimal totalDiscount = normalizeMoney(request.totalDiscount());
        BigDecimal grandTotal = normalizeMoney(request.grandTotal());

        BigDecimal computedTotalDiscount = normalizeMoney(lineDiscountTotal.add(cartDiscountTotal).add(shippingDiscountTotal));
        if (computedTotalDiscount.compareTo(totalDiscount) != 0) {
            throw new ValidationException("promotionPricing totalDiscount does not match line/cart/shipping discount totals");
        }
        if (shippingDiscountTotal.compareTo(shippingAmount) > 0) {
            throw new ValidationException("promotionPricing shippingDiscountTotal cannot exceed shippingAmount");
        }
        if (lineDiscountTotal.add(cartDiscountTotal).compareTo(subtotal) > 0) {
            throw new ValidationException("promotionPricing line/cart discounts cannot exceed subtotal");
        }

        BigDecimal expectedGrandTotal = normalizeMoney(
                subtotal.subtract(lineDiscountTotal).subtract(cartDiscountTotal).add(shippingAmount).subtract(shippingDiscountTotal)
        );
        if (expectedGrandTotal.compareTo(grandTotal) != 0) {
            throw new ValidationException("promotionPricing grandTotal does not match pricing breakdown");
        }

        String couponCode = trimToNull(request.couponCode());
        if (couponCode != null && request.couponReservationId() == null) {
            throw new ValidationException("promotionPricing.couponReservationId is required when couponCode is provided");
        }

        return new PromotionPricingSnapshot(
                request.couponReservationId(),
                couponCode,
                subtotal,
                lineDiscountTotal,
                cartDiscountTotal,
                shippingAmount,
                shippingDiscountTotal,
                totalDiscount,
                grandTotal
        );
    }

    private PromotionPricingSnapshot validateAndResolvePricingSnapshot(
            PromotionPricingSnapshot requestedPricing,
            BigDecimal itemSubtotal,
            BigDecimal computedShippingAmount
    ) {
        BigDecimal normalizedItemSubtotal = normalizeMoney(itemSubtotal);
        BigDecimal normalizedShippingAmount = normalizeMoney(computedShippingAmount);
        if (requestedPricing == null) {
            return PromotionPricingSnapshot.none(normalizedItemSubtotal, normalizedShippingAmount);
        }
        if (requestedPricing.subtotal().compareTo(normalizedItemSubtotal) != 0) {
            throw new ValidationException("promotionPricing subtotal does not match computed order subtotal");
        }
        if (requestedPricing.shippingAmount().compareTo(normalizedShippingAmount) != 0) {
            throw new ValidationException("promotionPricing shippingAmount does not match computed shipping fee");
        }
        return requestedPricing;
    }

    private BigDecimal calculateShippingForOrder(List<ResolvedOrderLine> lines, String destinationCountryCode) {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<ShippingFeeCalculator.ShippingLine> shippingLines = lines.stream()
                .map(line -> new ShippingFeeCalculator.ShippingLine(
                        line == null || line.product() == null ? null : line.product().vendorId(),
                        line == null ? 0 : line.quantity()
                ))
                .toList();
        return shippingFeeCalculator.calculate(shippingLines, destinationCountryCode);
    }

    private void maybeCommitCouponReservation(Order order, UUID expectedCustomerId, PromotionPricingSnapshot pricing) {
        if (order == null || pricing == null || pricing.couponReservationId() == null) {
            return;
        }
        CouponReservationResponse response = promotionClient.commitCouponReservation(pricing.couponReservationId(), order.getId());
        if (response == null) {
            throw new ValidationException("Coupon reservation commit response is missing");
        }
        if (response.customerId() == null || !response.customerId().equals(expectedCustomerId)) {
            tryReleaseReservationCompensation(pricing.couponReservationId(), "customer_mismatch_after_commit");
            throw new ValidationException("Coupon reservation does not belong to this customer");
        }
        if (response.quotedSubtotal() != null && normalizeMoney(response.quotedSubtotal()).compareTo(order.getSubtotal()) != 0) {
            tryReleaseReservationCompensation(pricing.couponReservationId(), "subtotal_mismatch_after_commit");
            throw new ValidationException("Coupon reservation subtotal does not match order subtotal");
        }
        if (response.quotedGrandTotal() != null && normalizeMoney(response.quotedGrandTotal()).compareTo(order.getOrderTotal()) != 0) {
            tryReleaseReservationCompensation(pricing.couponReservationId(), "grand_total_mismatch_after_commit");
            throw new ValidationException("Coupon reservation grand total does not match order total");
        }
        if (response.reservedDiscountAmount() != null && normalizeMoney(response.reservedDiscountAmount()).compareTo(order.getTotalDiscount()) != 0) {
            tryReleaseReservationCompensation(pricing.couponReservationId(), "discount_mismatch_after_commit");
            throw new ValidationException("Coupon reservation discount does not match order totalDiscount");
        }
        if (order.getCouponCode() == null && StringUtils.hasText(response.couponCode())) {
            order.setCouponCode(response.couponCode().trim());
            orderRepository.save(order);
        }
    }

    private void maybeReleaseCouponReservationForFinalStatus(Order order, OrderStatus nextStatus) {
        if (order == null || nextStatus == null) {
            return;
        }
        if (nextStatus != OrderStatus.CANCELLED && nextStatus != OrderStatus.REFUNDED) {
            return;
        }
        if (order.getCouponReservationId() == null) {
            return;
        }
        tryReleaseReservationCompensation(order.getCouponReservationId(), "order_" + nextStatus.name().toLowerCase(Locale.ROOT));
    }

    private void tryReleaseReservationCompensation(UUID reservationId, String reason) {
        if (reservationId == null) {
            return;
        }
        try {
            promotionClient.releaseCouponReservation(reservationId, safePromotionReleaseReason(reason));
        } catch (RuntimeException ex) {
            log.warn("Failed to release coupon reservation {} (reason: {}). " +
                    "Reservation may need manual reconciliation.", reservationId, reason, ex);
        }
    }

    private void maybeReserveInventory(Order order, List<ResolvedOrderLine> lines) {
        if (order == null || lines == null || lines.isEmpty()) return;
        List<StockCheckRequest> stockItems = lines.stream()
                .map(line -> new StockCheckRequest(line.product().id(), line.quantity()))
                .toList();
        Instant expiresAt = order.getExpiresAt() != null
                ? order.getExpiresAt()
                : Instant.now().plus(orderExpiryTtl);
        inventoryClient.reserveStock(order.getId(), stockItems, expiresAt);
    }

    private void maybeHandleInventoryForStatusTransition(Order order, OrderStatus nextStatus) {
        if (order == null || nextStatus == null) return;
        if (nextStatus == OrderStatus.CONFIRMED) {
            try {
                inventoryClient.confirmReservation(order.getId());
            } catch (RuntimeException ex) {
                log.warn("Inventory reservation confirmation failed for order {}. May need manual reconciliation.",
                        order.getId(), ex);
            }
        } else if (nextStatus == OrderStatus.CANCELLED || nextStatus == OrderStatus.PAYMENT_FAILED) {
            try {
                inventoryClient.releaseReservation(order.getId(), "order_" + nextStatus.name().toLowerCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                log.warn("Inventory reservation release failed for order {} (status: {}). May need manual reconciliation.",
                        order.getId(), nextStatus, ex);
            }
        }
    }

    private String safePromotionReleaseReason(String reason) {
        String normalized = trimToNull(reason);
        if (normalized == null) {
            return "order_final_status_release";
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    private record PromotionPricingSnapshot(
            UUID couponReservationId,
            String couponCode,
            BigDecimal subtotal,
            BigDecimal lineDiscountTotal,
            BigDecimal cartDiscountTotal,
            BigDecimal shippingAmount,
            BigDecimal shippingDiscountTotal,
            BigDecimal totalDiscount,
            BigDecimal grandTotal
    ) {
        private static PromotionPricingSnapshot none(BigDecimal subtotal, BigDecimal shippingAmount) {
            BigDecimal normalizedSubtotal = subtotal == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : subtotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal normalizedShippingAmount = shippingAmount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : shippingAmount.setScale(2, RoundingMode.HALF_UP);
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new PromotionPricingSnapshot(
                    null,
                    null,
                    normalizedSubtotal,
                    zero,
                    zero,
                    normalizedShippingAmount,
                    zero,
                    zero,
                    normalizedSubtotal.add(normalizedShippingAmount).setScale(2, RoundingMode.HALF_UP)
            );
        }
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
                            normalizeMoney(order.getOrderTotal()),
                            null,
                            null,
                            null
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
                        normalizeMoney(i.getLineTotal()),
                        normalizeMoney(i.getDiscountAmount()),
                        i.getFulfilledQuantity(),
                        i.getCancelledQuantity()
                ))
                .toList();
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == null) {
            throw new ValidationException("status is required");
        }
        if (current == null || current == next) {
            return;
        }
        Set<OrderStatus> allowed = switch (current) {
            case PENDING -> EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case PAYMENT_PENDING -> EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED);
            case PAYMENT_FAILED -> EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED);
            case CONFIRMED -> EnumSet.of(OrderStatus.PROCESSING, OrderStatus.ON_HOLD, OrderStatus.CANCELLED);
            case ON_HOLD -> EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            case PROCESSING -> EnumSet.of(OrderStatus.SHIPPED, OrderStatus.ON_HOLD, OrderStatus.CANCELLED);
            case SHIPPED -> EnumSet.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED);
            case DELIVERED -> EnumSet.of(OrderStatus.RETURN_REQUESTED, OrderStatus.CLOSED);
            case RETURN_REQUESTED -> EnumSet.of(OrderStatus.REFUND_PENDING, OrderStatus.RETURN_REJECTED, OrderStatus.CLOSED);
            case RETURN_REJECTED -> EnumSet.of(OrderStatus.CLOSED);
            case REFUND_PENDING -> EnumSet.of(OrderStatus.REFUNDED, OrderStatus.CLOSED);
            case REFUNDED, CANCELLED, CLOSED -> EnumSet.noneOf(OrderStatus.class);
        };
        if (!allowed.contains(next)) {
            throw new ValidationException("Invalid order status transition: " + current.name() + " -> " + next.name());
        }
    }

    private void applyRefundFieldsIfNeeded(Order order, OrderStatus next, String refundReason, BigDecimal refundAmount) {
        if (next == OrderStatus.REFUND_PENDING) {
            if (refundReason == null || refundReason.isBlank()) {
                throw new ValidationException("refundReason is required when transitioning to REFUND_PENDING");
            }
            order.setRefundReason(refundReason.trim());
            order.setRefundInitiatedAt(Instant.now());
        }
        if (next == OrderStatus.REFUNDED) {
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("refundAmount must be positive when transitioning to REFUNDED");
            }
            order.setRefundAmount(refundAmount);
            order.setRefundCompletedAt(Instant.now());
        }
    }

    private void applyRefundFieldsIfNeeded(VendorOrder vendorOrder, OrderStatus next, String refundReason, BigDecimal refundAmount) {
        if (next == OrderStatus.REFUND_PENDING) {
            if (refundReason == null || refundReason.isBlank()) {
                throw new ValidationException("refundReason is required when transitioning to REFUND_PENDING");
            }
            vendorOrder.setRefundReason(refundReason.trim());
            vendorOrder.setRefundInitiatedAt(Instant.now());
        }
        if (next == OrderStatus.REFUNDED) {
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("refundAmount must be positive when transitioning to REFUNDED");
            }
            vendorOrder.setRefundAmount(refundAmount);
            vendorOrder.setRefundCompletedAt(Instant.now());

            // Track cumulative partial refund amounts
            BigDecimal previousRefunded = vendorOrder.getRefundedAmount() == null
                    ? BigDecimal.ZERO : vendorOrder.getRefundedAmount();
            vendorOrder.setRefundedAmount(normalizeMoney(previousRefunded.add(refundAmount)));
        }
    }

    private void recordStatusAudit(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorSub,
            String actorRoles,
            String actorType,
            String changeSource,
            String note
    ) {
        orderStatusAuditRepository.save(OrderStatusAudit.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorSub(StringUtils.hasText(actorSub) ? actorSub.trim() : null)
                .actorRoles(StringUtils.hasText(actorRoles) ? actorRoles.trim() : null)
                .actorType(actorType)
                .changeSource(changeSource)
                .note(note)
                .build());
    }

    private void recordVendorOrderStatusAudit(
            VendorOrder vendorOrder,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorSub,
            String actorRoles,
            String actorType,
            String changeSource,
            String note
    ) {
        vendorOrderStatusAuditRepository.save(VendorOrderStatusAudit.builder()
                .vendorOrder(vendorOrder)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorSub(StringUtils.hasText(actorSub) ? actorSub.trim() : null)
                .actorRoles(StringUtils.hasText(actorRoles) ? actorRoles.trim() : null)
                .actorType(actorType)
                .changeSource(changeSource)
                .note(note)
                .build());
    }

    private OrderStatusAuditResponse toStatusAuditResponse(OrderStatusAudit audit) {
        return new OrderStatusAuditResponse(
                audit.getId(),
                audit.getFromStatus() == null ? null : audit.getFromStatus().name(),
                audit.getToStatus() == null ? null : audit.getToStatus().name(),
                audit.getActorSub(),
                audit.getActorRoles(),
                audit.getActorType(),
                audit.getChangeSource(),
                audit.getNote(),
                audit.getCreatedAt()
        );
    }

    private VendorOrderStatusAuditResponse toVendorOrderStatusAuditResponse(VendorOrderStatusAudit audit) {
        return new VendorOrderStatusAuditResponse(
                audit.getId(),
                audit.getFromStatus() == null ? null : audit.getFromStatus().name(),
                audit.getToStatus() == null ? null : audit.getToStatus().name(),
                audit.getActorSub(),
                audit.getActorRoles(),
                audit.getActorType(),
                audit.getChangeSource(),
                audit.getNote(),
                audit.getCreatedAt()
        );
    }

    private VendorOrderResponse toVendorOrderResponse(VendorOrder vendorOrder) {
        return new VendorOrderResponse(
                vendorOrder.getId(),
                vendorOrder.getOrder() == null ? null : vendorOrder.getOrder().getId(),
                vendorOrder.getVendorId(),
                vendorOrder.getStatus() == null ? null : vendorOrder.getStatus().name(),
                vendorOrder.getItemCount(),
                vendorOrder.getQuantity(),
                normalizeMoney(vendorOrder.getOrderTotal()),
                vendorOrder.getCurrency(),
                normalizeMoney(vendorOrder.getDiscountAmount()),
                normalizeMoney(vendorOrder.getShippingAmount()),
                normalizeMoney(vendorOrder.getPlatformFee()),
                normalizeMoney(vendorOrder.getPayoutAmount()),
                vendorOrder.getTrackingNumber(),
                vendorOrder.getTrackingUrl(),
                vendorOrder.getCarrierCode(),
                vendorOrder.getEstimatedDeliveryDate(),
                normalizeMoney(vendorOrder.getRefundAmount()),
                normalizeMoney(vendorOrder.getRefundedAmount()),
                vendorOrder.getRefundedQuantity(),
                vendorOrder.getRefundReason(),
                vendorOrder.getRefundInitiatedAt(),
                vendorOrder.getRefundCompletedAt(),
                vendorOrder.getCreatedAt(),
                vendorOrder.getUpdatedAt()
        );
    }

    private OrderStatus deriveAggregateOrderStatus(Order order) {
        List<VendorOrder> vendorOrders = order.getVendorOrders() == null ? List.of() : order.getVendorOrders();
        if (vendorOrders.isEmpty()) {
            return order.getStatus() == null ? OrderStatus.PENDING : order.getStatus();
        }
        Set<OrderStatus> statuses = vendorOrders.stream()
                .map(VendorOrder::getStatus)
                .filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        if (statuses.isEmpty()) {
            return OrderStatus.PENDING;
        }
        if (statuses.size() == 1) {
            return statuses.iterator().next();
        }

        // When CANCELLED is mixed with active statuses, ignore CANCELLED for aggregation
        Set<OrderStatus> terminalStatuses = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.CLOSED);
        Set<OrderStatus> activeStatuses = statuses.stream()
                .filter(s -> !terminalStatuses.contains(s))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        // If all vendor orders are terminal, pick the most prominent terminal status
        Set<OrderStatus> effectiveStatuses = activeStatuses.isEmpty() ? statuses : activeStatuses;

        if (effectiveStatuses.contains(OrderStatus.ON_HOLD)) return OrderStatus.ON_HOLD;
        if (effectiveStatuses.contains(OrderStatus.REFUND_PENDING)) return OrderStatus.REFUND_PENDING;
        if (effectiveStatuses.contains(OrderStatus.RETURN_REQUESTED)) return OrderStatus.RETURN_REQUESTED;
        if (effectiveStatuses.contains(OrderStatus.RETURN_REJECTED)) return OrderStatus.RETURN_REJECTED;
        if (effectiveStatuses.contains(OrderStatus.PAYMENT_PENDING)) return OrderStatus.PAYMENT_PENDING;
        if (effectiveStatuses.contains(OrderStatus.PAYMENT_FAILED)) return OrderStatus.PAYMENT_FAILED;
        if (effectiveStatuses.contains(OrderStatus.SHIPPED)) return OrderStatus.SHIPPED;
        if (effectiveStatuses.contains(OrderStatus.PROCESSING)) return OrderStatus.PROCESSING;
        if (effectiveStatuses.contains(OrderStatus.CONFIRMED)) return OrderStatus.CONFIRMED;
        if (effectiveStatuses.contains(OrderStatus.PENDING)) return OrderStatus.PENDING;
        if (effectiveStatuses.contains(OrderStatus.DELIVERED)) return OrderStatus.DELIVERED;
        if (effectiveStatuses.contains(OrderStatus.CLOSED)) return OrderStatus.CLOSED;
        if (effectiveStatuses.contains(OrderStatus.REFUNDED)) return OrderStatus.REFUNDED;
        if (effectiveStatuses.contains(OrderStatus.CANCELLED)) return OrderStatus.CANCELLED;
        return OrderStatus.PROCESSING;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public OrderResponse setPaymentInfo(UUID orderId, SetPaymentInfoRequest req) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getPaymentId() != null) {
            throw new ValidationException("Payment info is already set for order: " + orderId);
        }
        order.setPaymentId(req.paymentId());
        order.setPaymentMethod(req.paymentMethod());
        order.setPaymentGatewayRef(req.paymentGatewayRef());
        order.setPaidAt(Instant.now());
        Order saved = orderRepository.save(order);
        evictOrderCachesAfterStatusMutation();
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public OrderResponse cancelMyOrder(String keycloakId, UUID orderId, CancelOrderRequest req) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getCustomerId().equals(customer.id())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        if (!CUSTOMER_CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ValidationException("Order cannot be cancelled in status: " + order.getStatus().name());
        }
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        String note = req != null && req.reason() != null && !req.reason().isBlank()
                ? "Customer cancelled: " + req.reason().trim()
                : "Customer cancelled order";
        recordStatusAudit(saved, previousStatus, OrderStatus.CANCELLED, keycloakId, null, "customer", "customer_cancel", note);

        if (order.getVendorOrders() != null) {
            for (VendorOrder vo : order.getVendorOrders()) {
                if (vo.getStatus() != OrderStatus.CANCELLED) {
                    OrderStatus voPrevious = vo.getStatus();
                    vo.setStatus(OrderStatus.CANCELLED);
                    vendorOrderRepository.save(vo);
                    recordVendorOrderStatusAudit(vo, voPrevious, OrderStatus.CANCELLED, keycloakId, null, "customer", "customer_cancel", "Cancelled by customer");
                }
            }
        }

        final Order savedOrder = saved;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                maybeReleaseCouponReservationForFinalStatus(savedOrder, OrderStatus.CANCELLED);
                maybeHandleInventoryForStatusTransition(savedOrder, OrderStatus.CANCELLED);
            }
        });
        evictOrderCachesAfterStatusMutation();
        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public VendorOrderResponse setTrackingInfo(UUID vendorOrderId, SetTrackingInfoRequest req) {
        VendorOrder vendorOrder = vendorOrderRepository.findById(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor order not found: " + vendorOrderId));
        vendorOrder.setTrackingNumber(req.trackingNumber());
        vendorOrder.setTrackingUrl(req.trackingUrl());
        vendorOrder.setCarrierCode(req.carrierCode());
        vendorOrder.setEstimatedDeliveryDate(req.estimatedDeliveryDate());
        VendorOrder saved = vendorOrderRepository.save(vendorOrder);
        return toVendorOrderResponse(saved);
    }

    public List<VendorOrderResponse> getMyVendorOrders(String keycloakId, UUID orderId) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(keycloakId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getCustomerId().equals(customer.id())) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return vendorOrderRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(this::toVendorOrderResponse)
                .toList();
    }

    public VendorOrderDeletionCheckResponse getVendorDeletionCheck(UUID vendorId) {
        long totalOrders = vendorOrderRepository.countDistinctParentOrdersByVendorId(vendorId);
        long pendingOrders = vendorOrderRepository.countDistinctParentOrdersByVendorIdAndStatuses(
                vendorId,
                VENDOR_DELETION_BLOCKING_STATUSES
        );
        return new VendorOrderDeletionCheckResponse(
                vendorId,
                totalOrders,
                pendingOrders,
                vendorOrderRepository.findLatestParentOrderCreatedAtByVendorId(vendorId)
        );
    }

    @Transactional(readOnly = true)
    public CustomerProductPurchaseCheckResponse checkCustomerPurchasedProduct(UUID customerId, UUID productId) {
        List<UUID> orderIds = orderRepository.findDeliveredOrderIdsByCustomerAndProduct(customerId, productId);
        if (orderIds.isEmpty()) {
            return new CustomerProductPurchaseCheckResponse(false, null);
        }
        return new CustomerProductPurchaseCheckResponse(true, orderIds.getFirst());
    }

    // ── Vendor self-service ─────────────────────────────────────

    public Page<VendorOrderResponse> listVendorOrdersForVendorUser(
            String userSub, UUID vendorIdHint, OrderStatus status, Pageable pageable
    ) {
        UUID vendorId = resolveVendorIdForUser(userSub, vendorIdHint);
        Page<VendorOrder> page = status != null
                ? vendorOrderRepository.findByVendorIdAndStatusOrderByCreatedAtDesc(vendorId, status, pageable)
                : vendorOrderRepository.findByVendorIdOrderByCreatedAtDesc(vendorId, pageable);
        return page.map(this::toVendorOrderResponse);
    }

    public VendorOrderDetailResponse getVendorOrderForVendorUser(String userSub, UUID vendorIdHint, UUID vendorOrderId) {
        UUID vendorId = resolveVendorIdForUser(userSub, vendorIdHint);
        VendorOrder vo = vendorOrderRepository.findById(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor order not found: " + vendorOrderId));
        if (!vo.getVendorId().equals(vendorId)) {
            throw new ResourceNotFoundException("Vendor order not found: " + vendorOrderId);
        }
        Order parentOrder = vo.getOrder();
        List<OrderItemResponse> items = vo.getItems() == null ? List.of() : vo.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(), i.getProductId(), i.getVendorId(), i.getProductSku(),
                        i.getItem(), i.getQuantity(),
                        normalizeMoney(i.getUnitPrice()), normalizeMoney(i.getLineTotal()),
                        normalizeMoney(i.getDiscountAmount()),
                        i.getFulfilledQuantity(), i.getCancelledQuantity()
                ))
                .toList();
        OrderAddressResponse shippingAddress = parentOrder != null
                ? toAddressResponse(parentOrder.getShippingAddressId(), parentOrder.getShippingAddress())
                : null;
        return new VendorOrderDetailResponse(
                vo.getId(),
                parentOrder != null ? parentOrder.getId() : null,
                vo.getVendorId(),
                vo.getStatus() == null ? null : vo.getStatus().name(),
                vo.getItemCount(),
                vo.getQuantity(),
                normalizeMoney(vo.getOrderTotal()),
                vo.getCurrency(),
                normalizeMoney(vo.getDiscountAmount()),
                normalizeMoney(vo.getShippingAmount()),
                normalizeMoney(vo.getPlatformFee()),
                normalizeMoney(vo.getPayoutAmount()),
                vo.getTrackingNumber(),
                vo.getTrackingUrl(),
                vo.getCarrierCode(),
                vo.getEstimatedDeliveryDate(),
                normalizeMoney(vo.getRefundAmount()),
                normalizeMoney(vo.getRefundedAmount()),
                vo.getRefundedQuantity(),
                vo.getRefundReason(),
                vo.getRefundInitiatedAt(),
                vo.getRefundCompletedAt(),
                items,
                shippingAddress,
                vo.getCreatedAt(),
                vo.getUpdatedAt()
        );
    }

    // ── Invoice generation ─────────────────────────────────────

    public InvoiceResponse getInvoice(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        CustomerSummary customer = null;
        try {
            customer = customerClient.getCustomer(order.getCustomerId());
        } catch (Exception ex) {
            log.warn("Failed to fetch customer details for invoice, orderId={}", orderId, ex);
        }

        String customerName = customer != null ? customer.name() : null;
        String customerEmail = customer != null ? customer.email() : null;

        List<InvoiceResponse.InvoiceLineItem> invoiceItems;
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            invoiceItems = order.getOrderItems().stream()
                    .map(i -> new InvoiceResponse.InvoiceLineItem(
                            i.getItem(),
                            i.getQuantity(),
                            normalizeMoney(i.getUnitPrice()),
                            normalizeMoney(i.getLineTotal())
                    ))
                    .toList();
        } else {
            invoiceItems = List.of(new InvoiceResponse.InvoiceLineItem(
                    order.getItem(),
                    order.getQuantity(),
                    normalizeMoney(order.getOrderTotal()),
                    normalizeMoney(order.getOrderTotal())
            ));
        }

        String orderIdStr = order.getId().toString().replace("-", "");
        String idPrefix = orderIdStr.length() >= 8 ? orderIdStr.substring(0, 8) : orderIdStr;
        String invoiceNumber = "INV-" + idPrefix.toUpperCase() + "-" + order.getCreatedAt().toEpochMilli();

        return new InvoiceResponse(
                order.getId(),
                invoiceNumber,
                customerName,
                customerEmail,
                invoiceItems,
                normalizeMoney(order.getSubtotal()),
                normalizeMoney(order.getTotalDiscount()),
                normalizeMoney(order.getShippingAmount()),
                normalizeMoney(order.getOrderTotal()),
                order.getCurrency(),
                Instant.now()
        );
    }

    // ── CSV export ──────────────────────────────────────────────

    public String exportOrdersCsv(OrderStatus status, Instant createdAfter, Instant createdBefore) {
        List<Order> orders = orderRepository.findAll(
                OrderSpecifications.withFilters(null, null, status, createdAfter, createdBefore)
        );

        StringBuilder csv = new StringBuilder();
        csv.append("orderId,customerKeycloakId,customerEmail,status,grandTotal,currency,createdAt,updatedAt,itemCount\n");

        for (Order order : orders) {
            CustomerSummary customer = null;
            try {
                customer = customerClient.getCustomer(order.getCustomerId());
            } catch (Exception ex) {
                log.warn("Failed to fetch customer for CSV export, orderId={}", order.getId(), ex);
            }

            csv.append(escapeCsvField(order.getId().toString())).append(',');
            csv.append(escapeCsvField(order.getCustomerId().toString())).append(',');
            csv.append(escapeCsvField(customer != null ? customer.email() : "")).append(',');
            csv.append(escapeCsvField(order.getStatus() != null ? order.getStatus().name() : "")).append(',');
            csv.append(normalizeMoney(order.getOrderTotal()).toPlainString()).append(',');
            csv.append(escapeCsvField(order.getCurrency())).append(',');
            csv.append(escapeCsvField(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "")).append(',');
            csv.append(escapeCsvField(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : "")).append(',');
            csv.append(order.getItemCount());
            csv.append('\n');
        }

        return csv.toString();
    }

    private String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── Shipping address update ─────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public OrderResponse updateShippingAddress(UUID orderId, UpdateShippingAddressRequest req) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!SHIPPING_ADDRESS_EDITABLE_STATUSES.contains(order.getStatus())) {
            throw new ValidationException(
                    "Shipping address can only be updated when order status is PENDING, PAYMENT_PENDING, or CONFIRMED. Current status: " + order.getStatus().name()
            );
        }

        OrderAddressSnapshot updatedAddress = OrderAddressSnapshot.builder()
                .label(req.label())
                .recipientName(req.recipientName())
                .phone(req.phone())
                .line1(req.line1())
                .line2(req.line2())
                .city(req.city())
                .state(req.state())
                .postalCode(req.postalCode())
                .countryCode(req.countryCode())
                .build();

        order.setShippingAddress(updatedAddress);
        Order saved = orderRepository.save(order);
        evictOrderCachesAfterStatusMutation();
        return toResponse(saved);
    }

    private UUID resolveVendorIdForUser(String userSub, UUID vendorIdHint) {
        VendorSummaryForOrder vendor = vendorClient.getVendorForUser(userSub, vendorIdHint);
        return vendor.id();
    }

}
