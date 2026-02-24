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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CartService {
    private static final int MAX_DISTINCT_CART_ITEMS = 200;

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final OrderClient orderClient;
    private final PromotionClient promotionClient;
    private final CustomerClient customerClient;
    private final ShippingFeeCalculator shippingFeeCalculator;
    private final TransactionTemplate transactionTemplate;

    @Cacheable(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return cartRepository.findWithItemsByKeycloakId(normalizedKeycloakId)
                .map(this::toResponse)
                .orElseGet(() -> emptyCartResponse(normalizedKeycloakId));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse addItem(String keycloakId, AddCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantityToAdd = sanitizeQuantity(request.quantity());
        ProductDetails product = resolvePurchasableProduct(request.productId());
        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                    .orElseGet(() -> {
                        Cart created = Cart.builder()
                                .keycloakId(normalizedKeycloakId)
                                .build();
                        created.setItems(new java.util.ArrayList<>());
                        return created;
                    });

            CartItem existing = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(product.id()))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                if (cart.getItems().size() >= MAX_DISTINCT_CART_ITEMS) {
                    throw new ValidationException("Cart cannot contain more than " + MAX_DISTINCT_CART_ITEMS + " distinct items");
                }
                CartItem created = buildCartItem(cart, product, quantityToAdd);
                cart.getItems().add(created);
            } else {
                int mergedQuantity = sanitizeQuantity(existing.getQuantity() + quantityToAdd);
                existing.setQuantity(mergedQuantity);
                refreshCartItemSnapshot(existing, product);
                existing.setLineTotal(calculateLineTotal(existing.getUnitPrice(), existing.getQuantity()));
            }

            cart.setLastActivityAt(Instant.now());
            Cart saved = cartRepository.save(cart);
            return toResponse(saved);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CartResponse updateItem(String keycloakId, UUID itemId, UpdateCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantity = sanitizeQuantity(request.quantity());

        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

            CartItem item = cart.getItems().stream()
                    .filter(existing -> existing.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

            ProductDetails product = resolvePurchasableProduct(item.getProductId());

            item.setQuantity(quantity);
            refreshCartItemSnapshot(item, product);
            item.setLineTotal(calculateLineTotal(item.getUnitPrice(), item.getQuantity()));

            Cart saved = cartRepository.save(cart);
            return toResponse(saved);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void removeItem(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

        Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);
        }
        cartRepository.save(cart);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void clear(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CheckoutResponse checkout(String keycloakId, CheckoutCartRequest request, String idempotencyKey) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart previewCart = cartRepository.findWithItemsByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ValidationException("Cart is empty"));

        if (previewCart.getItems().isEmpty()) {
            throw new ValidationException("Cart is empty");
        }

        List<CartCheckoutLine> expectedSnapshot = checkoutSnapshot(previewCart);
        Map<UUID, ProductDetails> latestProductsById = expectedSnapshot.stream()
                .map(CartCheckoutLine::productId)
                .distinct()
                .collect(java.util.stream.Collectors.toMap(
                        productId -> productId,
                        this::resolvePurchasableProduct,
                        (a, b) -> a
                ));

        PreparedCheckout prepared = transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                    .orElseThrow(() -> new ValidationException("Cart is empty"));

            if (cart.getItems().isEmpty()) {
                throw new ValidationException("Cart is empty");
            }
            if (!checkoutSnapshot(cart).equals(expectedSnapshot)) {
                throw new ValidationException("Cart changed during checkout. Retry checkout.");
            }

            List<CreateMyOrderItemRequest> orderItems = new java.util.ArrayList<>(cart.getItems().size());
            int totalQuantity = 0;
            BigDecimal subtotal = BigDecimal.ZERO;

            for (CartItem cartItem : cart.getItems()) {
                ProductDetails latest = latestProductsById.get(cartItem.getProductId());
                if (latest == null) {
                    throw new ValidationException("Product is not available: " + cartItem.getProductId());
                }
                orderItems.add(new CreateMyOrderItemRequest(cartItem.getProductId(), cartItem.getQuantity()));
                totalQuantity += cartItem.getQuantity();
                subtotal = subtotal.add(calculateLineTotal(normalizeMoney(latest.sellingPrice()), cartItem.getQuantity()));
            }

            return new PreparedCheckout(orderItems, cart.getItems().size(), totalQuantity, normalizeMoney(subtotal));
        });
        if (prepared == null) {
            throw new ValidationException("Unable to prepare checkout");
        }

        CustomerSummary customer = customerClient.getCustomerByKeycloakId(normalizedKeycloakId);
        if (customer == null || customer.id() == null) {
            throw new ValidationException("Customer not found for checkout");
        }
        CustomerAddressSummary shippingAddress = customerClient.getCustomerAddress(customer.id(), request.shippingAddressId());
        if (shippingAddress == null || shippingAddress.deleted()) {
            throw new ValidationException("Shipping address is not available");
        }

        PromotionQuoteRequest quoteRequest = buildPromotionQuoteRequest(
                previewCart,
                latestProductsById,
                customer.id(),
                calculateShippingForCart(previewCart, latestProductsById, shippingAddress.countryCode()),
                request == null ? null : request.couponCode(),
                shippingAddress.countryCode()
        );
        PromotionQuoteResponse quotedPricing = promotionClient.quote(quoteRequest);
        if (quotedPricing == null) {
            throw new ValidationException("Promotion quote is unavailable");
        }

        CouponReservationResponse couponReservation = null;
        PromotionQuoteResponse authoritativeQuote = quotedPricing;
        if (StringUtils.hasText(request == null ? null : request.couponCode())) {
            couponReservation = promotionClient.reserveCoupon(new CreateCouponReservationRequest(
                    request.couponCode().trim(),
                    customer.id(),
                    quoteRequest,
                    downstreamPromotionReservationKey(idempotencyKey)
            ));
            if (couponReservation == null || couponReservation.id() == null) {
                throw new ValidationException("Coupon reservation failed");
            }
            if (couponReservation.quote() != null) {
                authoritativeQuote = couponReservation.quote();
            }
        }

        PromotionCheckoutPricingRequest promotionPricing = toPromotionCheckoutPricingRequest(
                authoritativeQuote,
                couponReservation == null ? null : couponReservation.id(),
                couponReservation != null && StringUtils.hasText(couponReservation.couponCode())
                        ? couponReservation.couponCode()
                        : (request == null ? null : trimToNull(request.couponCode()))
        );

        OrderResponse order;
        try {
            order = orderClient.createMyOrder(
                    normalizedKeycloakId,
                    request.shippingAddressId(),
                    request.billingAddressId(),
                    prepared.orderItems(),
                    promotionPricing,
                    downstreamOrderIdempotencyKey(idempotencyKey)
            );
        } catch (ValidationException ex) {
            if (couponReservation != null && couponReservation.id() != null) {
                releaseCouponReservationQuietly(couponReservation.id(), "order_create_failed:" + ex.getClass().getSimpleName());
            }
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("does not match") || msg.contains("subtotal") || msg.contains("grandTotal"))) {
                throw new ValidationException("Prices have changed since your checkout preview. Please review your cart and try again.");
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (couponReservation != null && couponReservation.id() != null) {
                releaseCouponReservationQuietly(couponReservation.id(), "order_create_failed:" + ex.getClass().getSimpleName());
            }
            throw ex;
        }

        boolean cartCleared = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                        .map(cart -> {
                            if (checkoutSnapshot(cart).equals(expectedSnapshot)) {
                                cart.getItems().clear();
                                cartRepository.save(cart);
                                return true;
                            }
                            return false;
                        })
                        .orElse(true)));

        return new CheckoutResponse(
                order.id(),
                prepared.itemCount(),
                prepared.totalQuantity(),
                promotionPricing == null ? null : promotionPricing.couponCode(),
                promotionPricing == null ? null : promotionPricing.couponReservationId(),
                promotionPricing == null ? prepared.subtotal() : normalizeMoney(promotionPricing.subtotal()),
                promotionPricing == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : normalizeMoney(promotionPricing.lineDiscountTotal()),
                promotionPricing == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : normalizeMoney(promotionPricing.cartDiscountTotal()),
                promotionPricing == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : normalizeMoney(promotionPricing.shippingAmount()),
                promotionPricing == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : normalizeMoney(promotionPricing.shippingDiscountTotal()),
                promotionPricing == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : normalizeMoney(promotionPricing.totalDiscount()),
                promotionPricing == null ? prepared.subtotal() : normalizeMoney(promotionPricing.grandTotal()),
                cartCleared
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public CheckoutPreviewResponse previewCheckout(String keycloakId, CheckoutPreviewRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart cart = cartRepository.findWithItemsByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ValidationException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
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
        for (CartItem item : cart.getItems()) {
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
                cart.getItems().size(),
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

    private int sanitizeQuantity(int quantity) {
        if (quantity < 1) {
            throw new ValidationException("Quantity must be at least 1");
        }
        if (quantity > 1000) {
            throw new ValidationException("Quantity must be 1000 or less");
        }
        return quantity;
    }

    private CartItem buildCartItem(Cart cart, ProductDetails product, int quantity) {
        BigDecimal unitPrice = normalizeMoney(product.sellingPrice());
        return CartItem.builder()
                .cart(cart)
                .productId(product.id())
                .productSlug(resolveSlug(product))
                .productName(product.name().trim())
                .productSku(product.sku().trim())
                .mainImage(resolveMainImage(product))
                .categoryIds(serializeCategoryIds(product.categoryIds()))
                .unitPrice(unitPrice)
                .quantity(quantity)
                .lineTotal(calculateLineTotal(unitPrice, quantity))
                .build();
    }

    private void refreshCartItemSnapshot(CartItem item, ProductDetails product) {
        item.setProductSlug(resolveSlug(product));
        item.setProductName(product.name().trim());
        item.setProductSku(product.sku().trim());
        item.setMainImage(resolveMainImage(product));
        item.setCategoryIds(serializeCategoryIds(product.categoryIds()));
        item.setUnitPrice(normalizeMoney(product.sellingPrice()));
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
        if (cart == null || cart.getItems() == null) {
            return List.of();
        }
        return cart.getItems().stream()
                .map(item -> new CartCheckoutLine(item.getProductId(), item.getQuantity()))
                .sorted(Comparator.comparing(line -> line.productId() == null ? "" : line.productId().toString()))
                .toList();
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> activeItems = cart.getItems().stream()
                .filter(item -> !item.isSavedForLater())
                .sorted(Comparator.comparing(CartItem::getProductName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toItemResponse)
                .toList();

        List<CartItemResponse> savedItems = cart.getItems().stream()
                .filter(CartItem::isSavedForLater)
                .sorted(Comparator.comparing(CartItem::getProductName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toItemResponse)
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

    private CartItemResponse toItemResponse(CartItem item) {
        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductSlug(),
                item.getProductName(),
                item.getProductSku(),
                item.getMainImage(),
                deserializeCategoryIds(item.getCategoryIds()),
                normalizeMoney(item.getUnitPrice()),
                item.getQuantity(),
                normalizeMoney(item.getLineTotal()),
                item.isSavedForLater()
        );
    }

    private CartResponse emptyCartResponse(String keycloakId) {
        return new CartResponse(
                null,
                keycloakId,
                List.of(),
                List.of(),
                0,
                0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null,
                null,
                null
        );
    }

    private String downstreamOrderIdempotencyKey(String incomingKey) {
        if (!StringUtils.hasText(incomingKey)) {
            return null;
        }
        return "cart-checkout::" + incomingKey.trim();
    }

    private String downstreamPromotionReservationKey(String incomingKey) {
        if (!StringUtils.hasText(incomingKey)) {
            return null;
        }
        return "cart-coupon-reserve::" + incomingKey.trim();
    }

    private PromotionQuoteRequest buildPromotionQuoteRequest(
            Cart cart,
            Map<UUID, ProductDetails> latestProductsById,
            UUID customerId,
            BigDecimal shippingAmount,
            String couponCode,
            String countryCode
    ) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ValidationException("Cart is empty");
        }
        List<PromotionQuoteLineRequest> quoteLines = new java.util.ArrayList<>(cart.getItems().size());
        for (CartItem item : cart.getItems()) {
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
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<ShippingFeeCalculator.ShippingLine> shippingLines = new java.util.ArrayList<>(cart.getItems().size());
        for (CartItem item : cart.getItems()) {
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
        Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        item.setSavedForLater(true);
        cart.setLastActivityAt(Instant.now());
        return toResponse(cartRepository.save(cart));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse moveToCart(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        item.setSavedForLater(false);
        cart.setLastActivityAt(Instant.now());
        return toResponse(cartRepository.save(cart));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public CartResponse updateNote(String keycloakId, UpdateCartNoteRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .orElseGet(() -> {
                    Cart created = Cart.builder()
                            .keycloakId(normalizedKeycloakId)
                            .build();
                    created.setItems(new java.util.ArrayList<>());
                    return created;
                });
        cart.setNote(request.note() != null ? request.note().trim() : null);
        cart.setLastActivityAt(Instant.now());
        return toResponse(cartRepository.save(cart));
    }

    private String serializeCategoryIds(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        return categoryIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<UUID> deserializeCategoryIds(String categoryIds) {
        if (categoryIds == null || categoryIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(categoryIds.split(","))
                .filter(s -> !s.isBlank())
                .map(UUID::fromString)
                .toList();
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

    private record CartCheckoutLine(UUID productId, int quantity) {
    }
}
