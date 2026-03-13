package com.rumal.cart_service.service;

import com.rumal.cart_service.client.CustomerClient;
import com.rumal.cart_service.client.OrderClient;
import com.rumal.cart_service.client.ProductClient;
import com.rumal.cart_service.client.PromotionClient;
import com.rumal.cart_service.client.VendorOperationalStateClient;
import com.rumal.cart_service.dto.AddCartItemRequest;
import com.rumal.cart_service.dto.AppliedPromotionPreviewResponse;
import com.rumal.cart_service.dto.CartItemResponse;
import com.rumal.cart_service.dto.CartResponse;
import com.rumal.cart_service.dto.CheckoutCartRequest;
import com.rumal.cart_service.dto.CheckoutPreviewRequest;
import com.rumal.cart_service.dto.CheckoutPreviewResponse;
import com.rumal.cart_service.dto.CheckoutResponse;
import com.rumal.cart_service.dto.CouponReservationResponse;
import com.rumal.cart_service.dto.CreateCouponReservationRequest;
import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.CustomerAddressSummary;
import com.rumal.cart_service.dto.CustomerSummary;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.ProductDetails;
import com.rumal.cart_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.cart_service.dto.PromotionQuoteLineRequest;
import com.rumal.cart_service.dto.PromotionQuoteRequest;
import com.rumal.cart_service.dto.PromotionQuoteResponse;
import com.rumal.cart_service.dto.RejectedPromotionPreviewResponse;
import com.rumal.cart_service.dto.UpdateCartItemRequest;
import com.rumal.cart_service.dto.UpdateCartNoteRequest;
import com.rumal.cart_service.dto.VendorOperationalStateResponse;
import com.rumal.cart_service.entity.Cart;
import com.rumal.cart_service.entity.CartItem;
import com.rumal.cart_service.exception.ResourceNotFoundException;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import com.rumal.cart_service.exception.ValidationException;
import com.rumal.cart_service.repo.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CartService {
    private static final int MAX_DISTINCT_CART_ITEMS = 100;
    private static final int MAX_ITEM_QUANTITY = 100;
    private static final String ORDER_CREATE_FAILED_PREFIX = "order_create_failed:";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CartRepository cartRepository;
    private final ActiveCartStoreService activeCartStoreService;
    private final ProductClient productClient;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final OrderClient orderClient;
    private final PromotionClient promotionClient;
    private final CustomerClient customerClient;
    private final ShippingFeeCalculator shippingFeeCalculator;

    @Cacheable(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return toResponse(loadCustomerCart(normalizedKeycloakId));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse getBySessionId(String guestCartId) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        return toResponse(loadGuestCart(normalizedGuestCartId));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse addItem(String keycloakId, AddCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantityToAdd = sanitizeQuantity(request.quantity());
        ProductDetails product = resolvePurchasableProduct(request.productId());
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            addOrMergeItem(cart, product.id(), quantityToAdd);
            saveCustomerCart(normalizedKeycloakId, cart);
            return toResponse(cart);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse addItemToSession(String guestCartId, AddCartItemRequest request) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        int quantityToAdd = sanitizeQuantity(request.quantity());
        ProductDetails product = resolvePurchasableProduct(request.productId());
        return activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            addOrMergeItem(cart, product.id(), quantityToAdd);
            saveGuestCart(normalizedGuestCartId, cart);
            return toResponse(cart);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse updateItem(String keycloakId, UUID itemId, UpdateCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantity = sanitizeQuantity(request.quantity());
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            CartItem item = findCartItem(cart, itemId);
            resolvePurchasableProduct(item.getProductId());
            item.setQuantity(quantity);
            touchCart(cart);
            saveCustomerCart(normalizedKeycloakId, cart);
            return toResponse(cart);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse updateSessionItem(String guestCartId, UUID itemId, UpdateCartItemRequest request) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        int quantity = sanitizeQuantity(request.quantity());
        return activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            CartItem item = findCartItem(cart, itemId);
            resolvePurchasableProduct(item.getProductId());
            item.setQuantity(quantity);
            touchCart(cart);
            saveGuestCart(normalizedGuestCartId, cart);
            return toResponse(cart);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void removeItem(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            removeCartItem(cart, itemId);
            saveCustomerCart(normalizedKeycloakId, cart);
            return true;
        });
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void removeSessionItem(String guestCartId, UUID itemId) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            removeCartItem(cart, itemId);
            saveGuestCart(normalizedGuestCartId, cart);
            return true;
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void clear(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            cart.getItems().clear();
            touchCart(cart);
            saveCustomerCart(normalizedKeycloakId, cart);
            return true;
        });
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void clearSession(String guestCartId) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            cart.getItems().clear();
            touchCart(cart);
            saveGuestCart(normalizedGuestCartId, cart);
            return true;
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse mergeSessionIntoCustomerCart(String keycloakId, String guestCartId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () ->
                activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
                    Cart customerCart = loadCustomerCart(normalizedKeycloakId);
                    Cart guestCart = loadGuestCart(normalizedGuestCartId);
                    for (CartItem guestItem : guestCart.getItems()) {
                        addOrMergeItem(customerCart, guestItem.getProductId(), guestItem.getQuantity(), guestItem.isSavedForLater());
                    }
                    if (!StringUtils.hasText(customerCart.getNote()) && StringUtils.hasText(guestCart.getNote())) {
                        customerCart.setNote(guestCart.getNote().trim());
                    }
                    saveCustomerCart(normalizedKeycloakId, customerCart);
                    activeCartStoreService.deleteGuestCart(normalizedGuestCartId);
                    return toResponse(customerCart);
                }));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CheckoutResponse checkout(String keycloakId, CheckoutCartRequest request, String idempotencyKey) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        CheckoutContext checkoutContext = prepareCheckout(normalizedKeycloakId);
        CheckoutCustomerContext customerContext = resolveCheckoutCustomer(normalizedKeycloakId, request);
        PromotionPricingContext promotionContext = resolvePromotionPricing(
                checkoutContext.previewCart(),
                checkoutContext.latestProductsById(),
                customerContext,
                request,
                idempotencyKey
        );
        OrderResponse order = createOrderForCheckout(
                normalizedKeycloakId,
                request,
                checkoutContext.prepared(),
                promotionContext,
                idempotencyKey
        );
        boolean cartCleared = clearCheckedOutItems(normalizedKeycloakId, checkoutContext.expectedSnapshot());
        return buildCheckoutResponse(order, checkoutContext.prepared(), promotionContext.pricing(), cartCleared);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public CheckoutPreviewResponse previewCheckout(String keycloakId, CheckoutPreviewRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart cart = loadCustomerCart(normalizedKeycloakId);
        List<CartItem> activeItems = activeCartItems(cart);
        if (activeItems.isEmpty()) {
            throw new ValidationException("Cart is empty");
        }

        List<CartCheckoutLine> snapshot = checkoutSnapshot(cart);
        Map<UUID, ProductDetails> latestProductsById = snapshot.stream()
                .map(CartCheckoutLine::productId)
                .distinct()
                .collect(java.util.stream.Collectors.toMap(
                        productId -> productId,
                        this::resolvePurchasableProduct,
                        (a, b) -> a
                ));

        CustomerSummary customer = customerClient.getCustomerByKeycloakId(normalizedKeycloakId);
        if (customer == null || customer.id() == null) {
            throw new ValidationException("Customer not found for checkout preview");
        }

        int totalQuantity = 0;
        for (CartItem item : activeItems) {
            ProductDetails latest = latestProductsById.get(item.getProductId());
            if (latest == null) {
                throw new ValidationException("Product is not available: " + item.getProductId());
            }
            totalQuantity += item.getQuantity();
        }

        PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(
                cart,
                latestProductsById,
                customer.id(),
                calculateShippingForCart(
                        cart,
                        latestProductsById,
                        request == null ? null : request.countryCode()
                ),
                request == null ? null : request.couponCode(),
                request == null ? null : request.countryCode()
        );
        PromotionQuoteResponse quote = promotionClient.quote(quoteRequest);
        if (quote == null) {
            throw new ValidationException("Promotion quote is unavailable");
        }

        return new CheckoutPreviewResponse(
                activeItems.size(),
                totalQuantity,
                quoteRequest.couponCode(),
                normalizeMoney(quote.subtotal()),
                normalizeMoney(quote.lineDiscountTotal()),
                normalizeMoney(quote.cartDiscountTotal()),
                normalizeMoney(quote.shippingAmount()),
                normalizeMoney(quote.shippingDiscountTotal()),
                normalizeMoney(quote.totalDiscount()),
                normalizeMoney(quote.grandTotal()),
                quote.appliedPromotions() == null ? List.of() : quote.appliedPromotions().stream()
                        .map(entry -> new AppliedPromotionPreviewResponse(
                                entry.promotionId(),
                                entry.promotionName(),
                                entry.applicationLevel(),
                                entry.benefitType(),
                                entry.priority(),
                                entry.exclusive(),
                                normalizeMoney(entry.discountAmount())
                        ))
                        .toList(),
                quote.rejectedPromotions() == null ? List.of() : quote.rejectedPromotions().stream()
                        .map(entry -> new RejectedPromotionPreviewResponse(
                                entry.promotionId(),
                                entry.promotionName(),
                                entry.reason()
                        ))
                        .toList(),
                quote.pricedAt()
        );
    }

    private CheckoutContext prepareCheckout(String normalizedKeycloakId) {
        Cart previewCart = loadCustomerCart(normalizedKeycloakId);
        requireActiveCartItems(previewCart);
        List<CartCheckoutLine> expectedSnapshot = checkoutSnapshot(previewCart);
        Map<UUID, ProductDetails> latestProductsById = resolveCheckoutProducts(expectedSnapshot);
        PreparedCheckout prepared = prepareLockedCheckout(normalizedKeycloakId, expectedSnapshot, latestProductsById);
        return new CheckoutContext(previewCart, expectedSnapshot, latestProductsById, prepared);
    }

    private void requireActiveCartItems(Cart cart) {
        if (activeCartItems(cart).isEmpty()) {
            throw new ValidationException("Cart is empty");
        }
    }

    private Map<UUID, ProductDetails> resolveCheckoutProducts(List<CartCheckoutLine> expectedSnapshot) {
        return expectedSnapshot.stream()
                .map(CartCheckoutLine::productId)
                .distinct()
                .collect(java.util.stream.Collectors.toMap(
                        productId -> productId,
                        this::resolvePurchasableProduct,
                        (a, b) -> a
                ));
    }

    private PreparedCheckout prepareLockedCheckout(
            String normalizedKeycloakId,
            List<CartCheckoutLine> expectedSnapshot,
            Map<UUID, ProductDetails> latestProductsById
    ) {
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            requireActiveCartItems(cart);
            if (!checkoutSnapshot(cart).equals(expectedSnapshot)) {
                throw new ValidationException("Cart changed during checkout. Retry checkout.");
            }
            return buildPreparedCheckout(cart, latestProductsById);
        });
    }

    private PreparedCheckout buildPreparedCheckout(Cart cart, Map<UUID, ProductDetails> latestProductsById) {
        List<CartItem> activeItems = activeCartItems(cart);
        List<CreateMyOrderItemRequest> orderItems = new ArrayList<>(activeItems.size());
        int totalQuantity = 0;
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : activeItems) {
            ProductDetails latest = latestProductsById.get(cartItem.getProductId());
            if (latest == null) {
                throw new ValidationException("Product is not available: " + cartItem.getProductId());
            }
            orderItems.add(new CreateMyOrderItemRequest(cartItem.getProductId(), cartItem.getQuantity()));
            totalQuantity += cartItem.getQuantity();
            subtotal = subtotal.add(calculateLineTotal(normalizeMoney(latest.sellingPrice()), cartItem.getQuantity()));
        }

        return new PreparedCheckout(orderItems, cart.getItems().size(), totalQuantity, normalizeMoney(subtotal));
    }

    private CheckoutCustomerContext resolveCheckoutCustomer(String normalizedKeycloakId, CheckoutCartRequest request) {
        CustomerSummary customer = customerClient.getCustomerByKeycloakId(normalizedKeycloakId);
        if (customer == null || customer.id() == null) {
            throw new ValidationException("Customer not found for checkout");
        }
        CustomerAddressSummary shippingAddress = customerClient.getCustomerAddress(customer.id(), request.shippingAddressId());
        if (shippingAddress == null || shippingAddress.deleted()) {
            throw new ValidationException("Shipping address is not available");
        }
        return new CheckoutCustomerContext(customer, shippingAddress);
    }

    private PromotionPricingContext resolvePromotionPricing(
            Cart previewCart,
            Map<UUID, ProductDetails> latestProductsById,
            CheckoutCustomerContext customerContext,
            CheckoutCartRequest request,
            String idempotencyKey
    ) {
        String requestedCouponCode = requestedCouponCode(request);
        PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(
                previewCart,
                latestProductsById,
                customerContext.customer().id(),
                calculateShippingForCart(previewCart, latestProductsById, customerContext.shippingAddress().countryCode()),
                requestedCouponCode,
                customerContext.shippingAddress().countryCode()
        );
        PromotionQuoteResponse quotedPricing = requirePromotionQuote(quoteRequest);
        CouponReservationResponse couponReservation = reserveCouponIfNeeded(
                requestedCouponCode,
                customerContext.customer().id(),
                quoteRequest,
                idempotencyKey
        );
        PromotionQuoteResponse authoritativeQuote = resolveAuthoritativeQuote(quotedPricing, couponReservation);
        PromotionCheckoutPricingRequest pricing = toPromotionCheckoutPricingRequest(
                authoritativeQuote,
                reservationId(couponReservation),
                resolveCheckoutCouponCode(requestedCouponCode, couponReservation)
        );
        return new PromotionPricingContext(couponReservation, pricing);
    }

    private PromotionQuoteResponse requirePromotionQuote(PromotionQuoteRequest quoteRequest) {
        PromotionQuoteResponse quote = promotionClient.quote(quoteRequest);
        if (quote == null) {
            throw new ValidationException("Promotion quote is unavailable");
        }
        return quote;
    }

    private CouponReservationResponse reserveCouponIfNeeded(
            String requestedCouponCode,
            UUID customerId,
            PromotionQuoteRequest quoteRequest,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(requestedCouponCode)) {
            return null;
        }
        CouponReservationResponse couponReservation = promotionClient.reserveCoupon(new CreateCouponReservationRequest(
                requestedCouponCode,
                customerId,
                quoteRequest,
                downstreamPromotionReservationKey(idempotencyKey)
        ));
        if (couponReservation == null || couponReservation.id() == null) {
            throw new ValidationException("Coupon reservation failed");
        }
        return couponReservation;
    }

    private PromotionQuoteResponse resolveAuthoritativeQuote(
            PromotionQuoteResponse quotedPricing,
            CouponReservationResponse couponReservation
    ) {
        if (couponReservation != null && couponReservation.quote() != null) {
            return couponReservation.quote();
        }
        return quotedPricing;
    }

    private UUID reservationId(CouponReservationResponse couponReservation) {
        return couponReservation == null ? null : couponReservation.id();
    }

    private String resolveCheckoutCouponCode(String requestedCouponCode, CouponReservationResponse couponReservation) {
        if (couponReservation != null && StringUtils.hasText(couponReservation.couponCode())) {
            return couponReservation.couponCode();
        }
        return requestedCouponCode;
    }

    private String requestedCouponCode(CheckoutCartRequest request) {
        return request == null ? null : trimToNull(request.couponCode());
    }

    private OrderResponse createOrderForCheckout(
            String normalizedKeycloakId,
            CheckoutCartRequest request,
            PreparedCheckout prepared,
            PromotionPricingContext promotionContext,
            String idempotencyKey
    ) {
        try {
            OrderResponse order = orderClient.createMyOrder(
                    normalizedKeycloakId,
                    request.shippingAddressId(),
                    request.billingAddressId(),
                    prepared.orderItems(),
                    promotionContext.pricing(),
                    downstreamOrderIdempotencyKey(idempotencyKey)
            );
            return requireOrderResponse(order);
        } catch (RuntimeException ex) {
            releaseFailedOrderReservation(promotionContext.couponReservation(), ex);
            throw translateCheckoutOrderException(ex);
        }
    }

    private OrderResponse requireOrderResponse(OrderResponse order) {
        if (order == null || order.id() == null) {
            throw new ServiceUnavailableException("Order service returned an invalid checkout response.");
        }
        return order;
    }

    private void releaseFailedOrderReservation(CouponReservationResponse couponReservation, RuntimeException ex) {
        if (couponReservation != null && couponReservation.id() != null) {
            releaseCouponReservationQuietly(
                    couponReservation.id(),
                    ORDER_CREATE_FAILED_PREFIX + ex.getClass().getSimpleName()
            );
        }
    }

    private RuntimeException translateCheckoutOrderException(RuntimeException ex) {
        if (ex instanceof ValidationException validationException && isPricingDrift(validationException.getMessage())) {
            return new ValidationException(
                    "Prices have changed since your checkout preview. Please review your cart and try again.",
                    validationException
            );
        }
        return ex;
    }

    private boolean isPricingDrift(String message) {
        return message != null
                && (message.contains("does not match")
                || message.contains("subtotal")
                || message.contains("grandTotal"));
    }

    private boolean clearCheckedOutItems(String normalizedKeycloakId, List<CartCheckoutLine> expectedSnapshot) {
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            if (!checkoutSnapshot(cart).equals(expectedSnapshot)) {
                return false;
            }
            cart.getItems().removeIf(item -> !item.isSavedForLater());
            saveCustomerCart(normalizedKeycloakId, cart);
            return true;
        });
    }

    private CheckoutResponse buildCheckoutResponse(
            OrderResponse order,
            PreparedCheckout prepared,
            PromotionCheckoutPricingRequest promotionPricing,
            boolean cartCleared
    ) {
        if (promotionPricing == null) {
            return new CheckoutResponse(
                    order.id(),
                    prepared.itemCount(),
                    prepared.totalQuantity(),
                    null,
                    null,
                    prepared.subtotal(),
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    ZERO_AMOUNT,
                    prepared.subtotal(),
                    cartCleared
            );
        }
        return new CheckoutResponse(
                order.id(),
                prepared.itemCount(),
                prepared.totalQuantity(),
                promotionPricing.couponCode(),
                promotionPricing.couponReservationId(),
                normalizeMoney(promotionPricing.subtotal()),
                normalizeMoney(promotionPricing.lineDiscountTotal()),
                normalizeMoney(promotionPricing.cartDiscountTotal()),
                normalizeMoney(promotionPricing.shippingAmount()),
                normalizeMoney(promotionPricing.shippingDiscountTotal()),
                normalizeMoney(promotionPricing.totalDiscount()),
                normalizeMoney(promotionPricing.grandTotal()),
                cartCleared
        );
    }

    private ProductDetails resolvePurchasableProduct(UUID productId) {
        ProductDetails product = productClient.getById(productId);
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
        assertVendorCanAcceptOrders(product.vendorId());
        return product;
    }

    private void assertVendorCanAcceptOrders(UUID vendorId) {
        VendorOperationalStateResponse state = vendorOperationalStateClient.getState(vendorId);
        if (state == null || state.deleted() || !state.active()) {
            throw new ValidationException("Vendor is unavailable for ordering: " + vendorId);
        }
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(state.status()))) {
            throw new ValidationException("Vendor is not active for ordering: " + vendorId);
        }
        if (!state.acceptingOrders() || !state.storefrontVisible()) {
            throw new ValidationException("Vendor is not accepting orders: " + vendorId);
        }
    }

    private String normalizeKeycloakId(String keycloakId) {
        String normalized = keycloakId == null ? "" : keycloakId.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new ValidationException("Missing authentication header");
        }
        return normalized;
    }

    private String normalizeGuestCartId(String guestCartId) {
        String normalized = guestCartId == null ? "" : guestCartId.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new ValidationException("Missing guest cart header");
        }
        return normalized;
    }

    private int sanitizeQuantity(int quantity) {
        if (quantity < 1) {
            throw new ValidationException("Quantity must be at least 1");
        }
        if (quantity > MAX_ITEM_QUANTITY) {
            throw new ValidationException("Quantity must be " + MAX_ITEM_QUANTITY + " or less");
        }
        return quantity;
    }

    private Cart loadCustomerCart(String keycloakId) {
        return activeCartStoreService.loadCustomerCart(keycloakId)
                .map(this::toTransientCart)
                .orElseGet(() -> cartRepository.findWithItemsByKeycloakId(keycloakId)
                        .map(this::toTransientCart)
                        .map(cart -> {
                            saveCustomerCart(keycloakId, cart);
                            return cart;
                        })
                        .orElseGet(() -> newTransientCart(keycloakId)));
    }

    private Cart loadGuestCart(String guestCartId) {
        return activeCartStoreService.loadGuestCart(guestCartId)
                .map(this::toTransientCart)
                .orElseGet(() -> newTransientCart(""));
    }

    private void saveCustomerCart(String keycloakId, Cart cart) {
        activeCartStoreService.saveCustomerCart(keycloakId, toActiveCartState(touchCart(cart), keycloakId));
    }

    private void saveGuestCart(String guestCartId, Cart cart) {
        activeCartStoreService.saveGuestCart(guestCartId, toActiveCartState(touchCart(cart), ""));
    }

    private Cart newTransientCart(String keycloakId) {
        Instant now = Instant.now();
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .keycloakId(keycloakId)
                .note(null)
                .items(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .lastActivityAt(now)
                .build();
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private Cart toTransientCart(ActiveCartState state) {
        Cart cart = Cart.builder()
                .id(state.id() == null ? UUID.randomUUID() : state.id())
                .keycloakId(state.keycloakId())
                .note(state.note())
                .items(new ArrayList<>())
                .createdAt(state.createdAt() == null ? Instant.now() : state.createdAt())
                .updatedAt(state.updatedAt() == null ? state.createdAt() : state.updatedAt())
                .lastActivityAt(state.lastActivityAt() == null ? Instant.now() : state.lastActivityAt())
                .build();
        List<CartItem> items = state.items().stream()
                .map(item -> CartItem.builder()
                        .id(item.id() == null ? UUID.randomUUID() : item.id())
                        .cart(cart)
                        .productId(item.productId())
                        .productSlug("")
                        .productName("")
                        .productSku("")
                        .quantity(item.quantity())
                        .lineTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .unitPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .savedForLater(item.savedForLater())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        cart.setItems(items);
        return cart;
    }

    private Cart toTransientCart(Cart persistedCart) {
        Cart cart = newTransientCart(persistedCart.getKeycloakId());
        cart.setId(persistedCart.getId() == null ? UUID.randomUUID() : persistedCart.getId());
        cart.setNote(persistedCart.getNote());
        cart.setCreatedAt(persistedCart.getCreatedAt() == null ? Instant.now() : persistedCart.getCreatedAt());
        cart.setUpdatedAt(persistedCart.getUpdatedAt() == null ? cart.getCreatedAt() : persistedCart.getUpdatedAt());
        cart.setLastActivityAt(persistedCart.getLastActivityAt() == null ? cart.getUpdatedAt() : persistedCart.getLastActivityAt());
        List<CartItem> items = persistedCart.getItems() == null ? List.of() : persistedCart.getItems();
        cart.setItems(items.stream()
                .map(item -> CartItem.builder()
                        .id(item.getId() == null ? UUID.randomUUID() : item.getId())
                        .cart(cart)
                        .productId(item.getProductId())
                        .productSlug(item.getProductSlug())
                        .productName(item.getProductName())
                        .productSku(item.getProductSku())
                        .mainImage(item.getMainImage())
                        .categoryIds(item.getCategoryIds())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .savedForLater(item.isSavedForLater())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
        return cart;
    }

    private ActiveCartState toActiveCartState(Cart cart, String keycloakId) {
        List<CartItem> cartItems = cart.getItems();
        List<ActiveCartItemState> items = cartItems == null
                ? List.of()
                : cartItems.stream().map(this::toActiveCartItemState).toList();
        return new ActiveCartState(
                resolveCartId(cart),
                keycloakId,
                trimToNull(cart.getNote()),
                items,
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getLastActivityAt()
        );
    }

    private UUID resolveCartId(Cart cart) {
        return cart.getId() == null ? UUID.randomUUID() : cart.getId();
    }

    private ActiveCartItemState toActiveCartItemState(CartItem item) {
        UUID itemId = item.getId() == null ? UUID.randomUUID() : item.getId();
        return new ActiveCartItemState(
                itemId,
                item.getProductId(),
                item.getQuantity(),
                item.isSavedForLater()
        );
    }

    private Cart touchCart(Cart cart) {
        Instant now = Instant.now();
        if (cart.getId() == null) {
            cart.setId(UUID.randomUUID());
        }
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }
        if (cart.getCreatedAt() == null) {
            cart.setCreatedAt(now);
        }
        cart.setUpdatedAt(now);
        cart.setLastActivityAt(now);
        return cart;
    }

    private void addOrMergeItem(Cart cart, UUID productId, int quantity) {
        addOrMergeItem(cart, productId, quantity, false);
    }

    private void addOrMergeItem(Cart cart, UUID productId, int quantity, boolean savedForLater) {
        CartItem existing = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getProductId(), productId) && item.isSavedForLater() == savedForLater)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            if (cart.getItems().size() >= MAX_DISTINCT_CART_ITEMS) {
                throw new ValidationException("Cart cannot contain more than " + MAX_DISTINCT_CART_ITEMS + " distinct items");
            }
            cart.getItems().add(buildCartItem(cart, productId, quantity, savedForLater));
        } else {
            existing.setQuantity(sanitizeQuantity(existing.getQuantity() + quantity));
        }
        touchCart(cart);
    }

    private void removeCartItem(Cart cart, UUID itemId) {
        boolean removed = cart.getItems().removeIf(item -> Objects.equals(item.getId(), itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);
        }
        touchCart(cart);
    }

    private CartItem findCartItem(Cart cart, UUID itemId) {
        return cart.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
    }

    private CartItem buildCartItem(Cart cart, UUID productId, int quantity, boolean savedForLater) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .productId(productId)
                .productSlug("")
                .productName("")
                .productSku("")
                .mainImage(null)
                .categoryIds(null)
                .unitPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .quantity(quantity)
                .lineTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .savedForLater(savedForLater)
                .build();
    }

    private String resolveSlug(ProductDetails product) {
        String slug = product.slug() == null ? "" : product.slug().trim();
        if (slug.isEmpty()) {
            throw new ValidationException("Product slug is missing: " + product.id());
        }
        return slug;
    }

    private String resolveMainImage(ProductDetails product) {
        List<String> images = product.images();
        if (images == null || images.isEmpty()) {
            return null;
        }
        String first = images.getFirst();
        if (first == null) {
            return null;
        }
        String trimmed = first.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal calculateLineTotal(BigDecimal unitPrice, int quantity) {
        return normalizeMoney(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private List<CartCheckoutLine> checkoutSnapshot(Cart cart) {
        return activeCartItems(cart).stream()
                .map(item -> new CartCheckoutLine(item.getProductId(), item.getQuantity()))
                .sorted(Comparator.comparing(line -> line.productId() == null ? "" : line.productId().toString()))
                .toList();
    }

    private List<CartItem> activeCartItems(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return List.of();
        }
        return cart.getItems().stream()
                .filter(item -> !item.isSavedForLater())
                .toList();
    }

    private CartResponse toResponse(Cart cart) {
        Map<UUID, ProductDetails> productsById = resolveProductsById(cart.getItems());
        List<CartItemResponse> activeItems = cart.getItems().stream()
                .filter(item -> !item.isSavedForLater())
                .map(item -> toItemResponse(item, productsById.get(item.getProductId())))
                .sorted(Comparator.comparing(CartItemResponse::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<CartItemResponse> savedItems = cart.getItems().stream()
                .filter(CartItem::isSavedForLater)
                .map(item -> toItemResponse(item, productsById.get(item.getProductId())))
                .sorted(Comparator.comparing(CartItemResponse::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalQuantity = activeItems.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();
        BigDecimal subtotal = activeItems.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new CartResponse(
                cart.getId(),
                cart.getKeycloakId(),
                activeItems,
                savedItems,
                activeItems.size(),
                totalQuantity,
                subtotal,
                cart.getNote(),
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }

    private Map<UUID, ProductDetails> resolveProductsById(List<CartItem> items) {
        Map<UUID, ProductDetails> productsById = new HashMap<>();
        if (items == null) {
            return productsById;
        }
        for (CartItem item : items) {
            UUID productId = item.getProductId();
            if (productId == null || productsById.containsKey(productId)) {
                continue;
            }
            try {
                productsById.put(productId, productClient.getById(productId));
            } catch (RuntimeException ex) {
                productsById.put(productId, null);
            }
        }
        return productsById;
    }

    private CartItemResponse toItemResponse(CartItem item, ProductDetails product) {
        String productName = product != null && StringUtils.hasText(product.name())
                ? product.name().trim()
                : "Unavailable product";
        String productSlug = product != null && StringUtils.hasText(product.slug())
                ? resolveSlug(product)
                : item.getProductId().toString();
        String productSku = product != null && StringUtils.hasText(product.sku())
                ? product.sku().trim()
                : "";
        List<UUID> categoryIds = product != null && product.categoryIds() != null
                ? List.copyOf(product.categoryIds())
                : List.of();
        BigDecimal unitPrice = product != null ? normalizeMoney(product.sellingPrice()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                productSlug,
                productName,
                productSku,
                product == null ? null : resolveMainImage(product),
                categoryIds,
                unitPrice,
                item.getQuantity(),
                calculateLineTotal(unitPrice, item.getQuantity()),
                item.isSavedForLater()
        );
    }

    private String downstreamOrderIdempotencyKey(String incomingKey) {
        if (!StringUtils.hasText(incomingKey)) {
            return null;
        }
        return "cart-checkout_" + incomingKey.trim();
    }

    private String downstreamPromotionReservationKey(String incomingKey) {
        if (!StringUtils.hasText(incomingKey)) {
            return null;
        }
        return "cart-coupon-reserve_" + incomingKey.trim();
    }

    private PromotionQuoteRequest buildPromotionQuoteRequest(
            Cart cart,
            Map<UUID, ProductDetails> latestProductsById,
            UUID customerId,
            BigDecimal shippingAmount,
            String couponCode,
            String countryCode
    ) {
        if (cart == null || cart.getItems() == null || activeCartItems(cart).isEmpty()) {
            throw new ValidationException("Cart is empty");
        }
        List<CartItem> activeItems = activeCartItems(cart);
        List<PromotionQuoteLineRequest> quoteLines = new java.util.ArrayList<>(activeItems.size());
        for (CartItem item : activeItems) {
            ProductDetails latest = latestProductsById.get(item.getProductId());
            if (latest == null) {
                throw new ValidationException("Product is not available: " + item.getProductId());
            }
            Set<UUID> categoryIdSet = latest.categoryIds() != null
                    ? new java.util.HashSet<>(latest.categoryIds())
                    : Set.of();
            quoteLines.add(new PromotionQuoteLineRequest(
                    latest.id(),
                    latest.vendorId(),
                    categoryIdSet,
                    normalizeMoney(latest.sellingPrice()),
                    item.getQuantity()
            ));
        }
        return new PromotionQuoteRequest(
                quoteLines,
                shippingAmount,
                customerId,
                trimToNull(couponCode),
                trimToNull(countryCode),
                null
        );
    }

    private BigDecimal calculateShippingForCart(
            Cart cart,
            Map<UUID, ProductDetails> latestProductsById,
            String destinationCountryCode
    ) {
        if (cart == null || cart.getItems() == null || activeCartItems(cart).isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<CartItem> activeItems = activeCartItems(cart);
        List<ShippingFeeCalculator.ShippingLine> shippingLines = new java.util.ArrayList<>(activeItems.size());
        for (CartItem item : activeItems) {
            ProductDetails latest = latestProductsById.get(item.getProductId());
            if (latest == null || latest.vendorId() == null) {
                throw new ValidationException("Product vendorId is missing: " + item.getProductId());
            }
            shippingLines.add(new ShippingFeeCalculator.ShippingLine(latest.vendorId(), item.getQuantity()));
        }
        return shippingFeeCalculator.calculate(shippingLines, destinationCountryCode);
    }

    private PromotionCheckoutPricingRequest toPromotionCheckoutPricingRequest(
            PromotionQuoteResponse quote,
            UUID couponReservationId,
            String couponCode
    ) {
        if (quote == null) {
            return null;
        }
        return new PromotionCheckoutPricingRequest(
                couponReservationId,
                trimToNull(couponCode),
                normalizeMoney(quote.subtotal()),
                normalizeMoney(quote.lineDiscountTotal()),
                normalizeMoney(quote.cartDiscountTotal()),
                normalizeMoney(quote.shippingAmount()),
                normalizeMoney(quote.shippingDiscountTotal()),
                normalizeMoney(quote.totalDiscount()),
                normalizeMoney(quote.grandTotal())
        );
    }

    private void releaseCouponReservationQuietly(UUID reservationId, String reason) {
        try {
            promotionClient.releaseCouponReservation(reservationId, safeReleaseReason(reason));
        } catch (RuntimeException ignored) {
            // Reservation will expire by TTL if release call fails.
        }
    }

    private String safeReleaseReason(String reason) {
        String normalized = trimToNull(reason);
        if (normalized == null) {
            return "checkout_failed";
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse saveForLater(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            CartItem item = findCartItem(cart, itemId);
            item.setSavedForLater(true);
            touchCart(cart);
            saveCustomerCart(normalizedKeycloakId, cart);
            return toResponse(cart);
        });
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse saveSessionItemForLater(String guestCartId, UUID itemId) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        return activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            CartItem item = findCartItem(cart, itemId);
            item.setSavedForLater(true);
            touchCart(cart);
            saveGuestCart(normalizedGuestCartId, cart);
            return toResponse(cart);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse moveToCart(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            CartItem item = findCartItem(cart, itemId);
            item.setSavedForLater(false);
            touchCart(cart);
            saveCustomerCart(normalizedKeycloakId, cart);
            return toResponse(cart);
        });
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse moveSessionItemToCart(String guestCartId, UUID itemId) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        return activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            CartItem item = findCartItem(cart, itemId);
            item.setSavedForLater(false);
            touchCart(cart);
            saveGuestCart(normalizedGuestCartId, cart);
            return toResponse(cart);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse updateNote(String keycloakId, UpdateCartNoteRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return activeCartStoreService.withCustomerCartLock(normalizedKeycloakId, () -> {
            Cart cart = loadCustomerCart(normalizedKeycloakId);
            cart.setNote(trimToNull(request.note()));
            touchCart(cart);
            saveCustomerCart(normalizedKeycloakId, cart);
            return toResponse(cart);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse updateSessionNote(String guestCartId, UpdateCartNoteRequest request) {
        String normalizedGuestCartId = normalizeGuestCartId(guestCartId);
        return activeCartStoreService.withGuestCartLock(normalizedGuestCartId, () -> {
            Cart cart = loadGuestCart(normalizedGuestCartId);
            cart.setNote(trimToNull(request.note()));
            touchCart(cart);
            saveGuestCart(normalizedGuestCartId, cart);
            return toResponse(cart);
        });
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record PreparedCheckout(
            List<CreateMyOrderItemRequest> orderItems,
            int itemCount,
            int totalQuantity,
            BigDecimal subtotal
    ) {
    }

    private record CheckoutContext(
            Cart previewCart,
            List<CartCheckoutLine> expectedSnapshot,
            Map<UUID, ProductDetails> latestProductsById,
            PreparedCheckout prepared
    ) {
    }

    private record CheckoutCustomerContext(
            CustomerSummary customer,
            CustomerAddressSummary shippingAddress
    ) {
    }

    private record PromotionPricingContext(
            CouponReservationResponse couponReservation,
            PromotionCheckoutPricingRequest pricing
    ) {
    }

    private record CartCheckoutLine(UUID productId, int quantity) {
    }
}
