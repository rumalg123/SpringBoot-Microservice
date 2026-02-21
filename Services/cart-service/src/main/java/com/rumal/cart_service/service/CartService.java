package com.rumal.cart_service.service;

import com.rumal.cart_service.client.OrderClient;
import com.rumal.cart_service.client.ProductClient;
import com.rumal.cart_service.dto.AddCartItemRequest;
import com.rumal.cart_service.dto.CartItemResponse;
import com.rumal.cart_service.dto.CartResponse;
import com.rumal.cart_service.dto.CheckoutCartRequest;
import com.rumal.cart_service.dto.CheckoutResponse;
import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.ProductDetails;
import com.rumal.cart_service.dto.UpdateCartItemRequest;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;
    private final OrderClient orderClient;

    @Cacheable(cacheNames = "cartByKeycloak", key = "#keycloakId")
    @Transactional(readOnly = true)
    public CartResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return cartRepository.findWithItemsByKeycloakId(normalizedKeycloakId)
                .map(this::toResponse)
                .orElseGet(() -> emptyCartResponse(normalizedKeycloakId));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId")
    })
    @Transactional
    public CartResponse addItem(String keycloakId, AddCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantityToAdd = sanitizeQuantity(request.quantity());
        ProductDetails product = resolvePurchasableProduct(request.productId());

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
            existing.setQuantity(existing.getQuantity() + quantityToAdd);
            refreshCartItemSnapshot(existing, product);
            existing.setLineTotal(calculateLineTotal(existing.getUnitPrice(), existing.getQuantity()));
        }

        Cart saved = cartRepository.save(cart);
        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId")
    })
    @Transactional
    public CartResponse updateItem(String keycloakId, UUID itemId, UpdateCartItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        int quantity = sanitizeQuantity(request.quantity());

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
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId")
    })
    @Transactional
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
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId")
    })
    @Transactional
    public void clear(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "cartByKeycloak", key = "#keycloakId")
    })
    @Transactional
    public CheckoutResponse checkout(String keycloakId, CheckoutCartRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        Cart cart = cartRepository.findWithItemsByKeycloakIdForUpdate(normalizedKeycloakId)
                .orElseThrow(() -> new ValidationException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new ValidationException("Cart is empty");
        }

        List<CreateMyOrderItemRequest> orderItems = cart.getItems().stream()
                .map(cartItem -> {
                    ProductDetails latest = resolvePurchasableProduct(cartItem.getProductId());
                    refreshCartItemSnapshot(cartItem, latest);
                    cartItem.setLineTotal(calculateLineTotal(cartItem.getUnitPrice(), cartItem.getQuantity()));
                    return new CreateMyOrderItemRequest(cartItem.getProductId(), cartItem.getQuantity());
                })
                .toList();

        OrderResponse order = orderClient.createMyOrder(
                normalizedKeycloakId,
                request.shippingAddressId(),
                request.billingAddressId(),
                orderItems
        );

        int totalQuantity = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int itemCount = cart.getItems().size();

        cart.getItems().clear();
        cartRepository.save(cart);

        return new CheckoutResponse(order.id(), itemCount, totalQuantity, subtotal);
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
        return product;
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
}
