package com.rumal.cart_service.service;

import com.rumal.cart_service.client.OrderClient;
import com.rumal.cart_service.client.ProductClient;
import com.rumal.cart_service.client.VendorOperationalStateClient;
import com.rumal.cart_service.dto.AddCartItemRequest;
import com.rumal.cart_service.dto.CartItemResponse;
import com.rumal.cart_service.dto.CartResponse;
import com.rumal.cart_service.dto.CheckoutCartRequest;
import com.rumal.cart_service.dto.CheckoutResponse;
import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.ProductDetails;
import com.rumal.cart_service.dto.UpdateCartItemRequest;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final OrderClient orderClient;
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
                CartItem created = buildCartItem(cart, product, quantityToAdd);
                cart.getItems().add(created);
            } else {
                int mergedQuantity = sanitizeQuantity(existing.getQuantity() + quantityToAdd);
                existing.setQuantity(mergedQuantity);
                refreshCartItemSnapshot(existing, product);
                existing.setLineTotal(calculateLineTotal(existing.getUnitPrice(), existing.getQuantity()));
            }

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
        UUID productId = cartRepository.findWithItemsByKeycloakId(normalizedKeycloakId)
                .flatMap(cart -> cart.getItems().stream()
                        .filter(existing -> existing.getId().equals(itemId))
                        .findFirst()
                        .map(CartItem::getProductId))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        ProductDetails product = resolvePurchasableProduct(productId);

        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

            CartItem item = cart.getItems().stream()
                    .filter(existing -> existing.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

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

        OrderResponse order = orderClient.createMyOrder(
                normalizedKeycloakId,
                request.shippingAddressId(),
                request.billingAddressId(),
                prepared.orderItems(),
                downstreamOrderIdempotencyKey(idempotencyKey)
        );

        transactionTemplate.executeWithoutResult(status -> cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .ifPresent(cart -> {
                    if (checkoutSnapshot(cart).equals(expectedSnapshot)) {
                        cart.getItems().clear();
                        cartRepository.save(cart);
                    }
                }));

        return new CheckoutResponse(order.id(), prepared.itemCount(), prepared.totalQuantity(), prepared.subtotal());
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
        List<CartItemResponse> items = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getProductName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toItemResponse)
                .toList();

        int totalQuantity = items.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new CartResponse(
                cart.getId(),
                cart.getKeycloakId(),
                items,
                items.size(),
                totalQuantity,
                subtotal,
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
                normalizeMoney(item.getUnitPrice()),
                item.getQuantity(),
                normalizeMoney(item.getLineTotal())
        );
    }

    private CartResponse emptyCartResponse(String keycloakId) {
        return new CartResponse(
                null,
                keycloakId,
                List.of(),
                0,
                0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
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
