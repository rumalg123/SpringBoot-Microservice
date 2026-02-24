package com.rumal.product_service.service;

import com.rumal.product_service.client.VendorOperationalStateClient;
import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductCatalogRead;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.repo.ProductCatalogReadRepository;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCatalogReadModelProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogReadModelProjector.class);
    private static final String TOKEN_DELIMITER = "|";

    private final ProductRepository productRepository;
    private final ProductCatalogReadRepository productCatalogReadRepository;
    private final VendorOperationalStateClient vendorOperationalStateClient;

    @Value("${catalog.rebuild.page-size:100}")
    private int rebuildPageSize;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Transactional
    public void upsert(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        ProductCatalogRead row = toReadRow(product, null);
        productCatalogReadRepository.save(row);
    }

    @Transactional
    public void upsertById(UUID productId) {
        if (productId == null) {
            return;
        }
        productRepository.findById(productId)
                .ifPresent(this::upsert);
    }

    @Transactional
    public void refreshParentVariationFlag(UUID parentProductId) {
        if (parentProductId == null) {
            return;
        }
        productRepository.findById(parentProductId)
                .ifPresent(this::upsert);
    }

    @Transactional
    public void rebuildAll() {
        Set<UUID> parentIdsWithChildren = productRepository.findParentIdsWithActiveVariationChildren();
        productCatalogReadRepository.deleteAllInBatch();

        int pageSize = rebuildPageSize > 0 ? rebuildPageSize : 100;
        int pageNumber = 0;
        int totalProcessed = 0;

        log.info("Starting paginated read model rebuild with page size {}", pageSize);

        Page<Product> page;
        do {
            page = productRepository.findAll(PageRequest.of(pageNumber, pageSize, Sort.by("createdAt")));
            List<ProductCatalogRead> rows = page.getContent().stream()
                    .map(product -> toReadRow(product, parentIdsWithChildren))
                    .toList();
            productCatalogReadRepository.saveAll(rows);
            totalProcessed += rows.size();
            log.info("Read model rebuild progress: processed {} / {} products (page {})",
                    totalProcessed, page.getTotalElements(), pageNumber);
            pageNumber++;
        } while (page.hasNext());

        log.info("Read model rebuild completed. Total products processed: {}", totalProcessed);
    }

    private ProductCatalogRead toReadRow(Product product, Set<UUID> parentIdsWithChildren) {
        Set<Category> categories = product.getCategories() == null
                ? Set.of()
                : product.getCategories();
        Instant now = Instant.now();
        Instant createdAt = product.getCreatedAt() != null
                ? product.getCreatedAt()
                : (product.getUpdatedAt() != null ? product.getUpdatedAt() : now);
        Instant updatedAt = product.getUpdatedAt() != null
                ? product.getUpdatedAt()
                : createdAt;

        String mainCategory = categories.stream()
                .filter(category -> category.getType() == CategoryType.PARENT)
                .map(Category::getName)
                .findFirst()
                .orElse(null);
        String mainCategorySlug = categories.stream()
                .filter(category -> category.getType() == CategoryType.PARENT)
                .map(Category::getSlug)
                .findFirst()
                .orElse(null);

        Set<String> subCategories = categories.stream()
                .filter(category -> category.getType() == CategoryType.SUB)
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> allCategoryNames = categories.stream()
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        boolean hasActiveVariationChild = product.getProductType() == ProductType.PARENT
                && hasActiveVariationChild(product.getId(), parentIdsWithChildren);

        String vendorName = resolveVendorName(product.getVendorId());

        return ProductCatalogRead.builder()
                .id(product.getId())
                .parentProductId(product.getParentProductId())
                .slug(product.getSlug())
                .name(product.getName())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .brandName(product.getBrandName())
                .brandNameLc(normalize(product.getBrandName()))
                .mainImage(resolveMainImage(product))
                .regularPrice(product.getRegularPrice())
                .discountedPrice(product.getDiscountedPrice())
                .sellingPrice(resolveSellingPrice(product))
                .sku(product.getSku())
                .mainCategory(mainCategory)
                .mainCategorySlug(mainCategorySlug)
                .subCategoryTokens(encodeTokens(subCategories))
                .subCategoryTokensLc(encodeTokensLower(subCategories))
                .categoryTokens(encodeTokens(allCategoryNames))
                .categoryTokensLc(encodeTokensLower(allCategoryNames))
                .productType(product.getProductType())
                .vendorId(product.getVendorId())
                .active(product.isActive())
                .deleted(product.isDeleted())
                .approvalStatus(product.getApprovalStatus())
                .hasActiveVariationChild(hasActiveVariationChild)
                .nameLc(normalize(product.getName()))
                .shortDescriptionLc(normalize(product.getShortDescription()))
                .descriptionLc(normalize(product.getDescription()))
                .skuLc(normalize(product.getSku()))
                .mainCategoryLc(normalize(mainCategory))
                .viewCount(product.getViewCount())
                .soldCount(product.getSoldCount())
                .vendorName(vendorName)
                .vendorNameLc(normalize(vendorName))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private String resolveMainImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        String first = product.getImages().getFirst();
        if (first == null) {
            return null;
        }
        String trimmed = first.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal resolveSellingPrice(Product product) {
        return product.getDiscountedPrice() != null ? product.getDiscountedPrice() : product.getRegularPrice();
    }

    private String resolveVendorName(UUID vendorId) {
        if (vendorId == null || !hasText(internalAuthSharedSecret)) {
            return null;
        }
        try {
            VendorOperationalStateResponse state = vendorOperationalStateClient.getState(vendorId, internalAuthSharedSecret);
            return state != null ? state.vendorName() : null;
        } catch (Exception ex) {
            log.warn("Failed to resolve vendor name for vendorId={}: {}", vendorId, ex.getMessage());
            return null;
        }
    }

    private boolean hasActiveVariationChild(UUID parentId, Set<UUID> parentIdsWithChildren) {
        if (parentId == null) {
            return false;
        }
        if (parentIdsWithChildren != null) {
            return parentIdsWithChildren.contains(parentId);
        }
        return productRepository.existsByParentProductIdAndDeletedFalseAndActiveTrueAndProductType(parentId, ProductType.VARIATION);
    }

    private String encodeTokens(Collection<String> values) {
        Set<String> ordered = values == null
                ? Set.of()
                : values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (ordered.isEmpty()) {
            return TOKEN_DELIMITER;
        }
        return TOKEN_DELIMITER + String.join(TOKEN_DELIMITER, ordered) + TOKEN_DELIMITER;
    }

    private String encodeTokensLower(Collection<String> values) {
        Set<String> ordered = values == null
                ? Set.of()
                : values.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (ordered.isEmpty()) {
            return TOKEN_DELIMITER;
        }
        return TOKEN_DELIMITER + String.join(TOKEN_DELIMITER, ordered) + TOKEN_DELIMITER;
    }

    private String normalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
