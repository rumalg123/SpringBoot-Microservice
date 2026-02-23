package com.rumal.wishlist_service.service;

import com.rumal.wishlist_service.client.ProductClient;
import com.rumal.wishlist_service.dto.AddWishlistItemRequest;
import com.rumal.wishlist_service.dto.ProductDetails;
import com.rumal.wishlist_service.dto.WishlistItemResponse;
import com.rumal.wishlist_service.dto.WishlistResponse;
import com.rumal.wishlist_service.entity.WishlistItem;
import com.rumal.wishlist_service.exception.ResourceNotFoundException;
import com.rumal.wishlist_service.exception.ValidationException;
import com.rumal.wishlist_service.repo.WishlistItemRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    @Cacheable(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        List<WishlistItem> items = wishlistItemRepository.findByKeycloakIdOrderByCreatedAtDesc(normalizedKeycloakId);
        return toResponse(normalizedKeycloakId, items);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WishlistResponse addItem(String keycloakId, AddWishlistItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        ProductDetails product = resolveWishlistProduct(request.productId());
        return transactionTemplate.execute(status -> {
            WishlistItem item = wishlistItemRepository.findByKeycloakIdAndProductId(normalizedKeycloakId, request.productId())
                    .orElseGet(WishlistItem::new);

            item.setKeycloakId(normalizedKeycloakId);
            item.setProductId(product.id());
            item.setProductSlug(resolveSlug(product));
            item.setProductName(resolveName(product));
            item.setProductType(resolveProductType(product));
            item.setMainImage(resolveMainImage(product));
            item.setSellingPriceSnapshot(normalizeMoney(product.sellingPrice()));
            wishlistItemRepository.save(item);

            List<WishlistItem> items = wishlistItemRepository.findByKeycloakIdOrderByCreatedAtDesc(normalizedKeycloakId);
            return toResponse(normalizedKeycloakId, items);
        });
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public void removeItem(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistItem item = wishlistItemRepository.findByIdAndKeycloakId(itemId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found: " + itemId));
        wishlistItemRepository.delete(item);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public void removeByProductId(String keycloakId, UUID productId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistItem item = wishlistItemRepository.findByKeycloakIdAndProductId(normalizedKeycloakId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found for product: " + productId));
        wishlistItemRepository.delete(item);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public void clear(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        wishlistItemRepository.deleteByKeycloakId(normalizedKeycloakId);
    }

    private ProductDetails resolveWishlistProduct(UUID productId) {
        ProductDetails product = productClient.getById(productId);
        if (!product.active() || product.deleted()) {
            throw new ValidationException("Product is not available: " + productId);
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

    private WishlistResponse toResponse(String keycloakId, List<WishlistItem> items) {
        List<WishlistItemResponse> responseItems = items.stream()
                .map(this::toItemResponse)
                .toList();
        return new WishlistResponse(
                keycloakId,
                responseItems,
                responseItems.size()
        );
    }

    private WishlistItemResponse toItemResponse(WishlistItem item) {
        return new WishlistItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductSlug(),
                item.getProductName(),
                item.getProductType(),
                item.getMainImage(),
                normalizeMoney(item.getSellingPriceSnapshot()),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private String resolveSlug(ProductDetails product) {
        String slug = product.slug() == null ? "" : product.slug().trim();
        if (slug.isEmpty()) {
            throw new ValidationException("Product slug is missing: " + product.id());
        }
        return slug;
    }

    private String resolveName(ProductDetails product) {
        String name = product.name() == null ? "" : product.name().trim();
        if (name.isEmpty()) {
            throw new ValidationException("Product name is missing: " + product.id());
        }
        return name;
    }

    private String resolveProductType(ProductDetails product) {
        String productType = product.productType() == null ? "" : product.productType().trim();
        if (productType.isEmpty()) {
            throw new ValidationException("Product type is missing: " + product.id());
        }
        return productType;
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

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
