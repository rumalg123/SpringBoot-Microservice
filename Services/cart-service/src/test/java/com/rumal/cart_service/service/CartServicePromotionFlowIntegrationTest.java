package com.rumal.cart_service.service;

import com.rumal.cart_service.client.CustomerClient;
import com.rumal.cart_service.client.OrderClient;
import com.rumal.cart_service.client.ProductClient;
import com.rumal.cart_service.client.PromotionClient;
import com.rumal.cart_service.client.VendorOperationalStateClient;
import com.rumal.cart_service.dto.CheckoutCartRequest;
import com.rumal.cart_service.dto.CheckoutResponse;
import com.rumal.cart_service.dto.CouponReservationResponse;
import com.rumal.cart_service.dto.CreateCouponReservationRequest;
import com.rumal.cart_service.dto.CustomerAddressSummary;
import com.rumal.cart_service.dto.CustomerSummary;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.ProductDetails;
import com.rumal.cart_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.cart_service.dto.PromotionQuoteRequest;
import com.rumal.cart_service.dto.PromotionQuoteResponse;
import com.rumal.cart_service.dto.VendorOperationalStateResponse;
import com.rumal.cart_service.entity.Cart;
import com.rumal.cart_service.entity.CartItem;
import com.rumal.cart_service.repo.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartServicePromotionFlowIntegrationTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ProductClient productClient;
    private VendorOperationalStateClient vendorOperationalStateClient;
    private OrderClient orderClient;
    private PromotionClient promotionClient;
    private CustomerClient customerClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        productClient = Mockito.mock(ProductClient.class);
        vendorOperationalStateClient = Mockito.mock(VendorOperationalStateClient.class);
        orderClient = Mockito.mock(OrderClient.class);
        promotionClient = Mockito.mock(PromotionClient.class);
        customerClient = Mockito.mock(CustomerClient.class);

        cartService = new CartService(
                cartRepository,
                productClient,
                vendorOperationalStateClient,
                orderClient,
                promotionClient,
                customerClient,
                new ShippingFeeCalculator(
                        new BigDecimal("4.99"),
                        new BigDecimal("0.80"),
                        new BigDecimal("3.50"),
                        "US"
                ),
                transactionTemplate
        );
    }

    @Test
    void checkout_usesPromotionQuoteAndReservation_thenClearsCartOnSuccess() {
        String keycloakId = "kc-user-1";
        UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID vendorId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID customerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID shippingAddressId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID billingAddressId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID couponReservationId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID orderId = UUID.fromString("12121212-1212-1212-1212-121212121212");

        persistCartWithOneItem(keycloakId, productId, 2, new BigDecimal("20.00"));

        when(productClient.getById(productId)).thenReturn(new ProductDetails(
                productId,
                vendorId,
                "test-product",
                "Test Product",
                "SKU-1",
                "CHILD",
                true,
                new BigDecimal("10.00"),
                List.of("img-1"),
                List.of()
        ));
        when(vendorOperationalStateClient.getState(vendorId)).thenReturn(new VendorOperationalStateResponse(
                vendorId,
                true,
                false,
                "ACTIVE",
                true,
                true
        ));
        when(customerClient.getCustomerByKeycloakId(keycloakId)).thenReturn(new CustomerSummary(customerId, "Customer", "c@example.com"));
        when(customerClient.getCustomerAddress(customerId, shippingAddressId)).thenReturn(address(shippingAddressId, customerId, "US"));

        PromotionQuoteResponse initialQuote = quote(
                "20.00",
                "0.00",
                "2.00",
                "6.59",
                "0.00",
                "2.00",
                "24.59"
        );
        PromotionQuoteResponse reservedQuote = quote(
                "20.00",
                "0.00",
                "3.00",
                "6.59",
                "0.00",
                "3.00",
                "23.59"
        );
        when(promotionClient.quote(any(PromotionQuoteRequest.class))).thenReturn(initialQuote);
        when(promotionClient.reserveCoupon(any(CreateCouponReservationRequest.class))).thenReturn(new CouponReservationResponse(
                couponReservationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SAVE3",
                "RESERVED",
                customerId,
                null,
                new BigDecimal("3.00"),
                new BigDecimal("20.00"),
                new BigDecimal("23.59"),
                Instant.parse("2026-02-23T10:00:00Z"),
                Instant.parse("2026-02-23T10:15:00Z"),
                null,
                null,
                null,
                reservedQuote
        ));
        when(orderClient.createMyOrder(
                eq(keycloakId),
                eq(shippingAddressId),
                eq(billingAddressId),
                anyList(),
                any(PromotionCheckoutPricingRequest.class),
                eq("cart-checkout::idem-123")
        )).thenReturn(new OrderResponse(orderId, customerId, "Test Product", 2, Instant.parse("2026-02-23T10:01:00Z")));

        CheckoutResponse response = cartService.checkout(
                keycloakId,
                new CheckoutCartRequest(shippingAddressId, billingAddressId, "SAVE3", null, null),
                "idem-123"
        );

        assertEquals(orderId, response.orderId());
        assertEquals(1, response.itemCount());
        assertEquals(2, response.totalQuantity());
        assertEquals("SAVE3", response.couponCode());
        assertEquals(couponReservationId, response.couponReservationId());
        assertEquals(new BigDecimal("20.00"), response.subtotal());
        assertEquals(new BigDecimal("6.59"), response.shippingAmount());
        assertEquals(new BigDecimal("3.00"), response.totalDiscount());
        assertEquals(new BigDecimal("23.59"), response.grandTotal());

        PromotionQuoteRequest quoteRequest = captureQuoteRequest();
        assertEquals(customerId, quoteRequest.customerId());
        assertEquals("SAVE3", quoteRequest.couponCode());
        assertEquals("US", quoteRequest.countryCode());
        assertEquals(new BigDecimal("6.59"), quoteRequest.shippingAmount());
        assertEquals(1, quoteRequest.lines().size());
        assertEquals(productId, quoteRequest.lines().getFirst().productId());
        assertEquals(vendorId, quoteRequest.lines().getFirst().vendorId());
        assertEquals(2, quoteRequest.lines().getFirst().quantity());

        CreateCouponReservationRequest reserveRequest = captureReserveRequest();
        assertEquals("SAVE3", reserveRequest.couponCode());
        assertEquals(customerId, reserveRequest.customerId());
        assertEquals("cart-coupon-reserve::idem-123", reserveRequest.requestKey());
        assertEquals(new BigDecimal("6.59"), reserveRequest.quoteRequest().shippingAmount());

        PromotionCheckoutPricingRequest pricingRequest = captureOrderPricingRequest(keycloakId, shippingAddressId, billingAddressId);
        assertEquals(couponReservationId, pricingRequest.couponReservationId());
        assertEquals("SAVE3", pricingRequest.couponCode());
        assertEquals(new BigDecimal("20.00"), pricingRequest.subtotal());
        assertEquals(new BigDecimal("6.59"), pricingRequest.shippingAmount());
        assertEquals(new BigDecimal("3.00"), pricingRequest.totalDiscount());
        assertEquals(new BigDecimal("23.59"), pricingRequest.grandTotal());

        Cart cartAfterCheckout = cartRepository.findWithItemsByKeycloakId(keycloakId).orElseThrow();
        assertTrue(cartAfterCheckout.getItems().isEmpty());

        verify(promotionClient, never()).releaseCouponReservation(any(UUID.class), any(String.class));
    }

    private void persistCartWithOneItem(String keycloakId, UUID productId, int quantity, BigDecimal lineTotal) {
        Cart cart = Cart.builder()
                .keycloakId(keycloakId)
                .items(new ArrayList<>())
                .build();
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productSlug("test-product")
                .productName("Test Product")
                .productSku("SKU-1")
                .mainImage("img-1")
                .unitPrice(new BigDecimal("10.00"))
                .quantity(quantity)
                .lineTotal(lineTotal)
                .build();
        cart.getItems().add(item);
        cartRepository.saveAndFlush(cart);
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

    private PromotionQuoteResponse quote(
            String subtotal,
            String lineDiscountTotal,
            String cartDiscountTotal,
            String shippingAmount,
            String shippingDiscountTotal,
            String totalDiscount,
            String grandTotal
    ) {
        return new PromotionQuoteResponse(
                new BigDecimal(subtotal),
                new BigDecimal(lineDiscountTotal),
                new BigDecimal(cartDiscountTotal),
                new BigDecimal(shippingAmount),
                new BigDecimal(shippingDiscountTotal),
                new BigDecimal(totalDiscount),
                new BigDecimal(grandTotal),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-02-23T10:00:00Z")
        );
    }

    private PromotionQuoteRequest captureQuoteRequest() {
        ArgumentCaptor<PromotionQuoteRequest> captor = ArgumentCaptor.forClass(PromotionQuoteRequest.class);
        verify(promotionClient).quote(captor.capture());
        return captor.getValue();
    }

    private CreateCouponReservationRequest captureReserveRequest() {
        ArgumentCaptor<CreateCouponReservationRequest> captor = ArgumentCaptor.forClass(CreateCouponReservationRequest.class);
        verify(promotionClient).reserveCoupon(captor.capture());
        return captor.getValue();
    }

    private PromotionCheckoutPricingRequest captureOrderPricingRequest(String keycloakId, UUID shippingAddressId, UUID billingAddressId) {
        ArgumentCaptor<PromotionCheckoutPricingRequest> pricingCaptor = ArgumentCaptor.forClass(PromotionCheckoutPricingRequest.class);
        verify(orderClient).createMyOrder(
                eq(keycloakId),
                eq(shippingAddressId),
                eq(billingAddressId),
                anyList(),
                pricingCaptor.capture(),
                eq("cart-checkout::idem-123")
        );
        return pricingCaptor.getValue();
    }
}
