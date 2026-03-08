package com.rumal.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.client.CustomerClient;
import com.rumal.order_service.client.InventoryClient;
import com.rumal.order_service.client.ProductClient;
import com.rumal.order_service.client.PromotionClient;
import com.rumal.order_service.client.VendorClient;
import com.rumal.order_service.client.VendorOperationalStateClient;
import com.rumal.order_service.config.CustomerDetailsMode;
import com.rumal.order_service.service.OutboxService;
import com.rumal.order_service.config.OrderAggregationProperties;
import com.rumal.order_service.dto.CouponReservationResponse;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.CreateOrderItemRequest;
import com.rumal.order_service.dto.CustomerAddressSummary;
import com.rumal.order_service.dto.CustomerSummary;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.dto.ProductSummary;
import com.rumal.order_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.order_service.dto.StockCheckResult;
import com.rumal.order_service.dto.VendorOperationalStateResponse;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.repo.OrderRepository;
import com.rumal.order_service.repo.OrderStatusAuditRepository;
import com.rumal.order_service.repo.OrderStatusAuditOutboxRepository;
import com.rumal.order_service.repo.VendorOrderRepository;
import com.rumal.order_service.repo.VendorOrderStatusAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderServicePromotionFlowIntegrationTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusAuditRepository orderStatusAuditRepository;

    @Autowired
    private VendorOrderRepository vendorOrderRepository;

    @Autowired
    private VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private OrderService orderService;
    private CustomerClient customerClient;
    private ProductClient productClient;
    private PromotionClient promotionClient;
    private InventoryClient inventoryClient;
    private VendorClient vendorClient;
    private VendorOperationalStateClient vendorOperationalStateClient;
    private OrderStatusAuditOutboxRepository orderStatusAuditOutboxRepository;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        customerClient = Mockito.mock(CustomerClient.class);
        productClient = Mockito.mock(ProductClient.class);
        promotionClient = Mockito.mock(PromotionClient.class);
        inventoryClient = Mockito.mock(InventoryClient.class);
        vendorClient = Mockito.mock(VendorClient.class);
        vendorOperationalStateClient = Mockito.mock(VendorOperationalStateClient.class);
        orderStatusAuditOutboxRepository = Mockito.mock(OrderStatusAuditOutboxRepository.class);
        OrderCacheVersionService orderCacheVersionService = Mockito.mock(OrderCacheVersionService.class);
        outboxService = Mockito.mock(OutboxService.class);
        OrderAnalyticsLiveUpdateService orderAnalyticsLiveUpdateService = Mockito.mock(OrderAnalyticsLiveUpdateService.class);
        when(orderStatusAuditOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        orderService = new OrderService(
                orderRepository,
                orderStatusAuditRepository,
                orderStatusAuditOutboxRepository,
                vendorOrderRepository,
                vendorOrderStatusAuditRepository,
                new OrderAuditRequestContextResolver(),
                new OrderAuditPayloadSanitizer(new ObjectMapper()),
                customerClient,
                productClient,
                promotionClient,
                inventoryClient,
                vendorClient,
                vendorOperationalStateClient,
                new ShippingFeeCalculator(
                        new BigDecimal("4.99"),
                        new BigDecimal("0.80"),
                        new BigDecimal("3.50"),
                        "US"
                ),
                new OrderAggregationProperties(CustomerDetailsMode.GRACEFUL),
                orderCacheVersionService,
                transactionTemplate,
                outboxService,
                orderAnalyticsLiveUpdateService
        );
        ReflectionTestUtils.setField(orderService, "orderExpiryTtl", Duration.ofMinutes(30));
    }

    @Test
    void createForKeycloak_delaysCouponCommit_untilOrderConfirmed() {
        String keycloakId = "kc-user-1";
        UUID customerId = UUID.fromString("10101010-1010-1010-1010-101010101010");
        UUID productId = UUID.fromString("20202020-2020-2020-2020-202020202020");
        UUID vendorId = UUID.fromString("30303030-3030-3030-3030-303030303030");
        UUID shippingAddressId = UUID.fromString("40404040-4040-4040-4040-404040404040");
        UUID billingAddressId = UUID.fromString("50505050-5050-5050-5050-505050505050");
        UUID couponReservationId = UUID.fromString("60606060-6060-6060-6060-606060606060");

        when(customerClient.getCustomerByKeycloakId(keycloakId)).thenReturn(new CustomerSummary(customerId, "Customer", "c@example.com"));
        when(productClient.getBatch(anyList())).thenReturn(List.of(new ProductSummary(
                productId,
                null,
                vendorId,
                "Test Product",
                "SKU-1",
                "CHILD",
                new BigDecimal("10.00"),
                true
        )));
        @SuppressWarnings("unchecked")
        Set<UUID> anyVendorIds = any(Set.class);
        when(vendorOperationalStateClient.batchGetStates(anyVendorIds)).thenReturn(Map.of(
                vendorId,
                new VendorOperationalStateResponse(vendorId, true, false, "ACTIVE", true, true)
        ));
        when(vendorClient.getVendorNames(anyList())).thenReturn(Map.of(vendorId, "Vendor One"));
        when(inventoryClient.checkAvailability(anyList())).thenReturn(List.of(
                new StockCheckResult(productId, 10, true, false, "IN_STOCK")
        ));
        when(customerClient.getCustomerAddress(customerId, shippingAddressId)).thenReturn(address(shippingAddressId, customerId, "US"));
        when(customerClient.getCustomerAddress(customerId, billingAddressId)).thenReturn(address(billingAddressId, customerId, "US"));

        when(promotionClient.getCouponReservation(eq(couponReservationId))).thenReturn(new CouponReservationResponse(
                couponReservationId,
                UUID.randomUUID(),
                null,
                "SAVE3",
                "RESERVED",
                customerId,
                null,
                new BigDecimal("3.00"),
                new BigDecimal("20.00"),
                new BigDecimal("23.59"),
                Instant.parse("2030-02-23T10:00:00Z"),
                Instant.parse("2030-02-23T10:15:00Z"),
                null,
                null,
                null
        ));

        CreateMyOrderRequest createRequest = new CreateMyOrderRequest(
                null,
                null,
                List.of(new CreateOrderItemRequest(productId, 2)),
                shippingAddressId,
                billingAddressId,
                new PromotionCheckoutPricingRequest(
                        couponReservationId,
                        "SAVE3",
                        new BigDecimal("20.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("3.00"),
                        new BigDecimal("6.59"),
                        new BigDecimal("0.00"),
                        new BigDecimal("3.00"),
                        new BigDecimal("23.59")
                ),
                null
        );

        OrderResponse created = orderService.createForKeycloak(keycloakId, createRequest);

        assertNotNull(created.id());
        assertEquals(customerId, created.customerId());
        assertEquals("PENDING", created.status());
        assertEquals(couponReservationId, created.couponReservationId());
        assertEquals("SAVE3", created.couponCode());
        assertEquals(new BigDecimal("20.00"), created.subtotal());
        assertEquals(new BigDecimal("6.59"), created.shippingAmount());
        assertEquals(new BigDecimal("3.00"), created.totalDiscount());
        assertEquals(new BigDecimal("23.59"), created.orderTotal());

        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("INVENTORY_RESERVE"), any(Map.class));
        verify(outboxService, never()).enqueue(eq("Order"), eq(created.id()), eq("COUPON_COMMIT"), any(Map.class));
        verify(outboxService, never()).enqueue(eq("Order"), eq(created.id()), eq("CONFIRM_INVENTORY_RESERVATION"), any(Map.class));

        OrderResponse confirmed = orderService.updateStatus(
                created.id(),
                OrderStatus.CONFIRMED,
                null,
                null,
                null,
                null,
                "payment-sync",
                "system"
        );
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals(couponReservationId, confirmed.couponReservationId());

        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("COUPON_COMMIT"), any(Map.class));
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("CONFIRM_INVENTORY_RESERVATION"), any(Map.class));

        Order stored = orderRepository.findById(created.id()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, stored.getStatus());
        assertEquals(couponReservationId, stored.getCouponReservationId());
        assertEquals(new BigDecimal("23.59"), stored.getOrderTotal());
    }

    @Test
    void createForKeycloak_paymentFailureReleasesCouponReservation() {
        String keycloakId = "kc-user-3";
        UUID customerId = UUID.fromString("70707070-7070-7070-7070-707070707070");
        UUID productId = UUID.fromString("80808080-8080-8080-8080-808080808080");
        UUID vendorId = UUID.fromString("90909090-9090-9090-9090-909090909090");
        UUID shippingAddressId = UUID.fromString("a0a0a0a0-a0a0-a0a0-a0a0-a0a0a0a0a0a0");
        UUID billingAddressId = UUID.fromString("b0b0b0b0-b0b0-b0b0-b0b0-b0b0b0b0b0b0");
        UUID couponReservationId = UUID.fromString("c0c0c0c0-c0c0-c0c0-c0c0-c0c0c0c0c0c0");

        when(customerClient.getCustomerByKeycloakId(keycloakId)).thenReturn(new CustomerSummary(customerId, "Customer", "c@example.com"));
        when(productClient.getBatch(anyList())).thenReturn(List.of(new ProductSummary(
                productId,
                null,
                vendorId,
                "Test Product",
                "SKU-3",
                "CHILD",
                new BigDecimal("10.00"),
                true
        )));
        @SuppressWarnings("unchecked")
        Set<UUID> anyVendorIds = any(Set.class);
        when(vendorOperationalStateClient.batchGetStates(anyVendorIds)).thenReturn(Map.of(
                vendorId,
                new VendorOperationalStateResponse(vendorId, true, false, "ACTIVE", true, true)
        ));
        when(vendorClient.getVendorNames(anyList())).thenReturn(Map.of(vendorId, "Vendor One"));
        when(inventoryClient.checkAvailability(anyList())).thenReturn(List.of(
                new StockCheckResult(productId, 10, true, false, "IN_STOCK")
        ));
        when(customerClient.getCustomerAddress(customerId, shippingAddressId)).thenReturn(address(shippingAddressId, customerId, "US"));
        when(customerClient.getCustomerAddress(customerId, billingAddressId)).thenReturn(address(billingAddressId, customerId, "US"));
        when(promotionClient.getCouponReservation(eq(couponReservationId))).thenReturn(new CouponReservationResponse(
                couponReservationId,
                UUID.randomUUID(),
                null,
                "SAVE3",
                "RESERVED",
                customerId,
                null,
                new BigDecimal("3.00"),
                new BigDecimal("20.00"),
                new BigDecimal("23.59"),
                Instant.parse("2030-02-23T10:00:00Z"),
                Instant.parse("2030-02-23T10:15:00Z"),
                null,
                null,
                null
        ));

        OrderResponse created = orderService.createForKeycloak(keycloakId, new CreateMyOrderRequest(
                null,
                null,
                List.of(new CreateOrderItemRequest(productId, 2)),
                shippingAddressId,
                billingAddressId,
                new PromotionCheckoutPricingRequest(
                        couponReservationId,
                        "SAVE3",
                        new BigDecimal("20.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("3.00"),
                        new BigDecimal("6.59"),
                        new BigDecimal("0.00"),
                        new BigDecimal("3.00"),
                        new BigDecimal("23.59")
                ),
                null
        ));

        OrderResponse paymentPending = orderService.updateStatus(
                created.id(),
                OrderStatus.PAYMENT_PENDING,
                "Payment session opened",
                null,
                null,
                null,
                "payment-sync",
                "system"
        );
        assertEquals("PAYMENT_PENDING", paymentPending.status());

        OrderResponse paymentFailed = orderService.updateStatus(
                created.id(),
                OrderStatus.PAYMENT_FAILED,
                "Payment gateway rejected charge",
                null,
                null,
                null,
                "payment-sync",
                "system"
        );

        assertEquals("PAYMENT_FAILED", paymentFailed.status());
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("RELEASE_COUPON_RESERVATION"), any(Map.class));
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("RELEASE_INVENTORY_RESERVATION"), any(Map.class));
    }

    @Test
    void createForKeycloak_zeroTotalOrder_confirmsImmediately_withoutPayment() {
        String keycloakId = "kc-user-4";
        UUID customerId = UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1");
        UUID productId = UUID.fromString("e2e2e2e2-e2e2-e2e2-e2e2-e2e2e2e2e2e2");
        UUID vendorId = UUID.fromString("f3f3f3f3-f3f3-f3f3-f3f3-f3f3f3f3f3f3");
        UUID shippingAddressId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        UUID billingAddressId = UUID.fromString("34343434-3434-3434-3434-343434343434");
        UUID couponReservationId = UUID.fromString("56565656-5656-5656-5656-565656565656");

        when(customerClient.getCustomerByKeycloakId(keycloakId)).thenReturn(new CustomerSummary(customerId, "Customer", "c@example.com"));
        when(productClient.getBatch(anyList())).thenReturn(List.of(new ProductSummary(
                productId,
                null,
                vendorId,
                "Free Order Product",
                "SKU-4",
                "CHILD",
                new BigDecimal("10.00"),
                true
        )));
        @SuppressWarnings("unchecked")
        Set<UUID> anyVendorIds = any(Set.class);
        when(vendorOperationalStateClient.batchGetStates(anyVendorIds)).thenReturn(Map.of(
                vendorId,
                new VendorOperationalStateResponse(vendorId, true, false, "ACTIVE", true, true)
        ));
        when(vendorClient.getVendorNames(anyList())).thenReturn(Map.of(vendorId, "Vendor One"));
        when(inventoryClient.checkAvailability(anyList())).thenReturn(List.of(
                new StockCheckResult(productId, 10, true, false, "IN_STOCK")
        ));
        when(customerClient.getCustomerAddress(customerId, shippingAddressId)).thenReturn(address(shippingAddressId, customerId, "US"));
        when(customerClient.getCustomerAddress(customerId, billingAddressId)).thenReturn(address(billingAddressId, customerId, "US"));
        when(promotionClient.getCouponReservation(eq(couponReservationId))).thenReturn(new CouponReservationResponse(
                couponReservationId,
                UUID.randomUUID(),
                null,
                "FREEALL",
                "RESERVED",
                customerId,
                null,
                new BigDecimal("26.59"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO.setScale(2),
                Instant.parse("2030-02-23T10:00:00Z"),
                Instant.parse("2030-02-23T10:15:00Z"),
                null,
                null,
                null
        ));

        OrderResponse created = orderService.createForKeycloak(keycloakId, new CreateMyOrderRequest(
                null,
                null,
                List.of(new CreateOrderItemRequest(productId, 2)),
                shippingAddressId,
                billingAddressId,
                new PromotionCheckoutPricingRequest(
                        couponReservationId,
                        "FREEALL",
                        new BigDecimal("20.00"),
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("6.59"),
                        new BigDecimal("6.59"),
                        new BigDecimal("26.59"),
                        BigDecimal.ZERO.setScale(2)
                ),
                null
        ));

        assertEquals("CONFIRMED", created.status());
        assertEquals(BigDecimal.ZERO.setScale(2), created.orderTotal());
        assertEquals("NO_CHARGE", created.paymentMethod());
        assertEquals("NO_PAYMENT_REQUIRED", created.paymentGatewayRef());
        assertNotNull(created.paidAt());
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("INVENTORY_RESERVE"), any(Map.class));
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("COUPON_COMMIT"), any(Map.class));
        verify(outboxService).enqueue(eq("Order"), eq(created.id()), eq("CONFIRM_INVENTORY_RESERVATION"), any(Map.class));
    }

    @Test
    void createForKeycloak_rejectsWhenInventoryInsufficient_beforePersistingOrder() {
        String keycloakId = "kc-user-2";
        UUID customerId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID productId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID vendorId = UUID.fromString("99999999-8888-7777-6666-555555555555");
        UUID shippingAddressId = UUID.fromString("12121212-3434-5656-7878-909090909090");
        UUID billingAddressId = UUID.fromString("31313131-4141-5151-6161-717171717171");

        when(customerClient.getCustomerByKeycloakId(keycloakId))
                .thenReturn(new CustomerSummary(customerId, "Customer Two", "c2@example.com"));
        when(productClient.getBatch(anyList())).thenReturn(List.of(new ProductSummary(
                productId,
                null,
                vendorId,
                "Out Of Stock Product",
                "SKU-2",
                "CHILD",
                new BigDecimal("15.00"),
                true
        )));
        @SuppressWarnings("unchecked")
        Set<UUID> anyVendorIds = any(Set.class);
        when(vendorOperationalStateClient.batchGetStates(anyVendorIds)).thenReturn(Map.of(
                vendorId,
                new VendorOperationalStateResponse(vendorId, true, false, "ACTIVE", true, true)
        ));
        when(inventoryClient.checkAvailability(anyList())).thenReturn(List.of(
                new StockCheckResult(productId, 0, false, false, "OUT_OF_STOCK")
        ));
        when(customerClient.getCustomerAddress(customerId, shippingAddressId)).thenReturn(address(shippingAddressId, customerId, "LK"));
        when(customerClient.getCustomerAddress(customerId, billingAddressId)).thenReturn(address(billingAddressId, customerId, "LK"));

        CreateMyOrderRequest createRequest = new CreateMyOrderRequest(
                null,
                null,
                List.of(new CreateOrderItemRequest(productId, 1)),
                shippingAddressId,
                billingAddressId,
                null,
                null
        );

        assertThrows(
                com.rumal.order_service.exception.ValidationException.class,
                () -> orderService.createForKeycloak(keycloakId, createRequest)
        );
        assertEquals(0L, orderRepository.count());
        verify(outboxService, never()).enqueue(any(), any(), any(), any());
    }

    private CustomerAddressSummary address(UUID addressId, UUID customerId, String countryCode) {
        return new CustomerAddressSummary(
                addressId,
                customerId,
                "Home",
                "Customer",
                "1234567890",
                "Line1",
                null,
                "City",
                "State",
                "12345",
                countryCode,
                true,
                true,
                false
        );
    }
}
