package com.rumal.wishlist_service.service;

import com.rumal.wishlist_service.client.CartClient;
import com.rumal.wishlist_service.client.ProductClient;
import com.rumal.wishlist_service.dto.AddWishlistItemRequest;
import com.rumal.wishlist_service.dto.CreateWishlistCollectionRequest;
import com.rumal.wishlist_service.dto.ProductDetails;
import com.rumal.wishlist_service.dto.SharedWishlistResponse;
import com.rumal.wishlist_service.dto.UpdateItemNoteRequest;
import com.rumal.wishlist_service.dto.UpdateWishlistCollectionRequest;
import com.rumal.wishlist_service.dto.WishlistCollectionResponse;
import com.rumal.wishlist_service.dto.WishlistItemResponse;
import com.rumal.wishlist_service.dto.WishlistResponse;
import com.rumal.wishlist_service.entity.WishlistCollection;
import com.rumal.wishlist_service.entity.WishlistItem;
import com.rumal.wishlist_service.exception.ResourceNotFoundException;
import com.rumal.wishlist_service.exception.ValidationException;
import com.rumal.wishlist_service.repo.WishlistCollectionRepository;
import com.rumal.wishlist_service.repo.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    private final WishlistItemRepository wishlistItemRepository;
    private final WishlistCollectionRepository wishlistCollectionRepository;
    private final ProductClient productClient;
    private final CartClient cartClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${wishlist.max-items-per-collection:200}")
    private int maxItemsPerCollection;

    // ──────────────────────────────────────────────────────────────────────────
    //  Legacy flat-wishlist endpoints (backwards-compatible)
    // ──────────────────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        List<WishlistItem> items = wishlistItemRepository.findByKeycloakIdOrderByCreatedAtDesc(normalizedKeycloakId);
        return toResponse(normalizedKeycloakId, items);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public Page<WishlistItemResponse> getByKeycloakId(String keycloakId, Pageable pageable) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        return wishlistItemRepository.findByKeycloakId(normalizedKeycloakId, pageable).map(this::toItemResponse);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WishlistResponse addItem(String keycloakId, AddWishlistItemRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        ProductDetails product = resolveWishlistProduct(request.productId());
        return transactionTemplate.execute(status -> {
            WishlistCollection collection = resolveCollection(normalizedKeycloakId, request.collectionId());

            long currentCount = wishlistItemRepository.countByCollection(collection);
            if (currentCount >= maxItemsPerCollection) {
                throw new ValidationException(
                        "Collection has reached the maximum of " + maxItemsPerCollection + " items");
            }

            WishlistItem item = wishlistItemRepository
                    .findByCollectionAndProductId(collection, request.productId())
                    .orElseGet(WishlistItem::new);

            item.setKeycloakId(normalizedKeycloakId);
            item.setCollection(collection);
            item.setProductId(product.id());
            item.setProductSlug(resolveSlug(product));
            item.setProductName(resolveName(product));
            item.setProductType(resolveProductType(product));
            item.setMainImage(resolveMainImage(product));
            item.setSellingPriceSnapshot(normalizeMoney(product.sellingPrice()));
            if (request.note() != null) {
                item.setNote(request.note().trim().isEmpty() ? null : request.note().trim());
            }
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

    // ──────────────────────────────────────────────────────────────────────────
    //  Collection management
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public List<WishlistCollectionResponse> getCollections(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        List<WishlistCollection> collections = wishlistCollectionRepository
                .findByKeycloakIdOrderByCreatedAtAsc(normalizedKeycloakId);
        return collections.stream()
                .map(this::toCollectionResponse)
                .toList();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistCollectionResponse createCollection(String keycloakId, CreateWishlistCollectionRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

        long count = wishlistCollectionRepository.countByKeycloakId(normalizedKeycloakId);
        if (count >= 20) {
            throw new ValidationException("Maximum of 20 collections allowed");
        }

        WishlistCollection collection = WishlistCollection.builder()
                .keycloakId(normalizedKeycloakId)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .isDefault(false)
                .build();

        wishlistCollectionRepository.save(collection);
        return toCollectionResponse(collection);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistCollectionResponse updateCollection(String keycloakId, UUID collectionId,
                                                       UpdateWishlistCollectionRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistCollection collection = wishlistCollectionRepository
                .findByIdAndKeycloakId(collectionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

        collection.setName(request.name().trim());
        collection.setDescription(request.description() != null ? request.description().trim() : null);
        wishlistCollectionRepository.save(collection);
        return toCollectionResponse(collection);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public void deleteCollection(String keycloakId, UUID collectionId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistCollection collection = wishlistCollectionRepository
                .findByIdAndKeycloakId(collectionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

        if (collection.isDefault()) {
            throw new ValidationException("Cannot delete the default collection");
        }

        wishlistItemRepository.deleteByCollection(collection);
        wishlistCollectionRepository.delete(collection);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Collection-scoped item operations
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistCollectionResponse getCollectionWithItems(String keycloakId, UUID collectionId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistCollection collection = wishlistCollectionRepository
                .findByIdAndKeycloakId(collectionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));
        return toCollectionResponse(collection);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Sharing
    // ──────────────────────────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistCollectionResponse enableSharing(String keycloakId, UUID collectionId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistCollection collection = wishlistCollectionRepository
                .findByIdAndKeycloakId(collectionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

        if (!collection.isShared()) {
            collection.setShared(true);
            collection.setShareToken(UUID.randomUUID().toString().replace("-", ""));
            wishlistCollectionRepository.save(collection);
        }

        return toCollectionResponse(collection);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void revokeSharing(String keycloakId, UUID collectionId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistCollection collection = wishlistCollectionRepository
                .findByIdAndKeycloakId(collectionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

        collection.setShared(false);
        collection.setShareToken(null);
        wishlistCollectionRepository.save(collection);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public SharedWishlistResponse getSharedWishlist(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            throw new ValidationException("Invalid share token");
        }
        WishlistCollection collection = wishlistCollectionRepository.findByShareToken(shareToken.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Shared wishlist not found"));

        if (!collection.isShared()) {
            throw new ResourceNotFoundException("Shared wishlist not found");
        }

        List<WishlistItem> items = wishlistItemRepository.findByCollectionOrderByCreatedAtDesc(collection);
        List<WishlistItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return new SharedWishlistResponse(
                collection.getName(),
                collection.getDescription(),
                itemResponses,
                itemResponses.size()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Move to cart
    // ──────────────────────────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void moveItemToCart(String keycloakId, UUID itemId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistItem item = wishlistItemRepository.findByIdAndKeycloakId(itemId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found: " + itemId));

        UUID productId = item.getProductId();

        // Add to cart first — if this fails, item stays in wishlist (no data loss)
        cartClient.addItemToCart(normalizedKeycloakId, productId, 1);

        // Only delete from wishlist after cart add succeeds
        wishlistItemRepository.delete(item);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Item notes
    // ──────────────────────────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(cacheNames = "wishlistByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    })
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public WishlistItemResponse updateItemNote(String keycloakId, UUID itemId, UpdateItemNoteRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        WishlistItem item = wishlistItemRepository.findByIdAndKeycloakId(itemId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found: " + itemId));

        String note = request.note() != null ? request.note().trim() : null;
        item.setNote(note != null && note.isEmpty() ? null : note);
        wishlistItemRepository.save(item);
        return toItemResponse(item);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────────────────────

    private WishlistCollection resolveCollection(String keycloakId, UUID collectionId) {
        if (collectionId != null) {
            return wishlistCollectionRepository.findByIdAndKeycloakId(collectionId, keycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));
        }
        return getOrCreateDefaultCollection(keycloakId);
    }

    private WishlistCollection getOrCreateDefaultCollection(String keycloakId) {
        return wishlistCollectionRepository.findByKeycloakIdAndIsDefaultTrue(keycloakId)
                .orElseGet(() -> {
                    try {
                        WishlistCollection defaultCollection = WishlistCollection.builder()
                                .keycloakId(keycloakId)
                                .name("My Wishlist")
                                .isDefault(true)
                                .build();
                        return wishlistCollectionRepository.save(defaultCollection);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // Concurrent creation race — re-fetch the one that won
                        return wishlistCollectionRepository.findByKeycloakIdAndIsDefaultTrue(keycloakId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Default collection not found after concurrent creation", e));
                    }
                });
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

    private WishlistCollectionResponse toCollectionResponse(WishlistCollection collection) {
        List<WishlistItem> items = wishlistItemRepository.findByCollectionOrderByCreatedAtDesc(collection);
        List<WishlistItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return new WishlistCollectionResponse(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                collection.isDefault(),
                collection.isShared(),
                collection.getShareToken(),
                itemResponses,
                itemResponses.size(),
                collection.getCreatedAt(),
                collection.getUpdatedAt()
        );
    }

    private WishlistItemResponse toItemResponse(WishlistItem item) {
        return new WishlistItemResponse(
                item.getId(),
                item.getCollection() != null ? item.getCollection().getId() : null,
                item.getProductId(),
                item.getProductSlug(),
                item.getProductName(),
                item.getProductType(),
                item.getMainImage(),
                normalizeMoney(item.getSellingPriceSnapshot()),
                item.getNote(),
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
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
