package com.rumal.product_service.service;

import com.rumal.product_service.client.VendorOperationalStateClient;
import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.dto.ProductVariationAttributeRequest;
import com.rumal.product_service.dto.ProductVariationAttributeResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.ProductCatalogRead;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.entity.ProductVariationAttribute;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ServiceUnavailableException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.ProductCatalogReadRepository;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class ProductServiceImpl implements ProductService {

    private static final UUID ADMIN_VENDOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+\\.[A-Za-z0-9]{2,8}$");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCatalogReadRepository productCatalogReadRepository;
    private final ProductCatalogReadModelProjector productCatalogReadModelProjector;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final ProductCacheVersionService productCacheVersionService;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse create(UpsertProductRequest request) {
        if (request.productType() == ProductType.VARIATION) {
            throw new ValidationException("Use /admin/products/{parentId}/variations to create variation products");
        }
        Product product = new Product();
        applyUpsertRequest(product, request, null, null);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse createVariation(UUID parentId, UpsertProductRequest request) {
        Product parent = getActiveEntityById(parentId);
        if (parent.getProductType() != ProductType.PARENT) {
            throw new ValidationException("Variations can be added only to parent products");
        }
        if (request.productType() != ProductType.VARIATION) {
            throw new ValidationException("Variation endpoint requires productType=VARIATION");
        }

        Product variation = new Product();
        applyUpsertRequest(variation, request, parent.getId(), parent);
        validateVariationAgainstParent(parent, variation);
        Product saved = productRepository.save(variation);
        productCatalogReadModelProjector.upsert(saved);
        productCatalogReadModelProjector.refreshParentVariationFlag(parent.getId());
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "@productCacheVersionService.productByIdVersion() + '::id::' + #id")
    public ProductResponse getById(UUID id) {
        Product product = getActiveEntityById(id);
        return toPublicResponse(product);
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "@productCacheVersionService.productByIdVersion() + '::slug::' + #slug")
    public ProductResponse getBySlug(String slug) {
        Product product = getActiveEntityBySlug(slug);
        return toPublicResponse(product);
    }

    @Override
    public ProductResponse getByIdOrSlug(String idOrSlug) {
        UUID parsedId = tryParseUuid(idOrSlug);
        if (parsedId != null) {
            try {
                return getById(parsedId);
            } catch (ResourceNotFoundException ignored) {
                // Fall through to slug lookup.
            }
        }
        return getBySlug(idOrSlug);
    }

    @Override
    public boolean isSlugAvailable(String slug, UUID excludeId) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (normalizedSlug.isEmpty()) {
            return false;
        }
        if (excludeId == null) {
            return !productRepository.existsBySlug(normalizedSlug);
        }
        return !productRepository.existsBySlugAndIdNot(normalizedSlug, excludeId);
    }

    @Override
    @Cacheable(cacheNames = "productsList", key = "@productCacheVersionService.productsListVersion() + '::variations::' + #parentId")
    public List<ProductSummaryResponse> listVariations(UUID parentId) {
        Product parent = getActiveEntityById(parentId);
        return listVariationsForParent(parent);
    }

    @Override
    @Cacheable(cacheNames = "productsList", key = "@productCacheVersionService.productsListVersion() + '::variationsByAny::' + #parentIdOrSlug")
    public List<ProductSummaryResponse> listVariationsByIdOrSlug(String parentIdOrSlug) {
        Product parent = resolveProductByIdOrSlug(parentIdOrSlug);
        return listVariationsForParent(parent);
    }

    private List<ProductSummaryResponse> listVariationsForParent(Product parent) {
        if (parent.getProductType() != ProductType.PARENT) {
            throw new ValidationException("Variations are available only for parent products");
        }
        if (!parent.isActive()) {
            throw new ResourceNotFoundException("Product not found: " + parent.getId());
        }
        assertVendorStorefrontVisible(parent.getVendorId(), parent.getId());
        List<Product> variations = productRepository.findByParentProductIdAndDeletedFalseAndActiveTrue(parent.getId());
        Map<UUID, VendorOperationalStateResponse> states = resolveVendorStates(
                variations.stream().map(Product::getVendorId).toList()
        );
        return variations.stream()
                .filter(product -> product.getProductType() == ProductType.VARIATION)
                .filter(product -> isVendorVisibleForStorefront(product.getVendorId(), states))
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Cacheable(
            cacheNames = "productsList",
            key = "@productCacheVersionService.productsListVersion() + '::' + T(com.rumal.product_service.service.ProductServiceImpl).listCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice,#includeOrphanParents)"
    )
    public Page<ProductSummaryResponse> list(
            Pageable pageable,
            String q,
            String sku,
            String category,
            String mainCategory,
            String subCategory,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice,
            boolean includeOrphanParents
    ) {
        Specification<ProductCatalogRead> spec = buildReadFilterSpec(
                q,
                sku,
                category,
                mainCategory,
                subCategory,
                vendorId,
                type,
                minSellingPrice,
                maxSellingPrice,
                includeOrphanParents
        )
                .and((root, query, cb) -> cb.isFalse(root.get("deleted")))
                .and((root, query, cb) -> cb.isTrue(root.get("active")));
        Page<ProductSummaryResponse> page = productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
        return filterHiddenVendorProducts(page, pageable);
    }

    @Override
    @Cacheable(
            cacheNames = "deletedProductsList",
            key = "@productCacheVersionService.deletedProductsListVersion() + '::' + T(com.rumal.product_service.service.ProductServiceImpl).deletedListCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice)"
    )
    public Page<ProductSummaryResponse> listDeleted(
            Pageable pageable,
            String q,
            String sku,
            String category,
            String mainCategory,
            String subCategory,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice
    ) {
        Specification<ProductCatalogRead> spec = buildReadFilterSpec(
                q,
                sku,
                category,
                mainCategory,
                subCategory,
                vendorId,
                type,
                minSellingPrice,
                maxSellingPrice,
                true
        )
                .and((root, query, cb) -> cb.isTrue(root.get("deleted")));
        return productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse update(UUID id, UpsertProductRequest request) {
        Product product = getActiveEntityById(id);
        UUID parentId = product.getParentProductId();
        Product parent = null;
        if (parentId != null && request.productType() != ProductType.VARIATION) {
            throw new ValidationException("Existing variation product must keep productType=VARIATION");
        }
        if (parentId == null && request.productType() == ProductType.VARIATION) {
            throw new ValidationException("Use variation endpoint to create variation products under a parent");
        }
        if (parentId != null) {
            parent = getActiveEntityById(parentId);
        }
        applyUpsertRequest(product, request, parentId, parent);
        if (parentId != null) {
            validateVariationAgainstParent(parent, product);
        }
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        if (parentId != null) {
            productCatalogReadModelProjector.refreshParentVariationFlag(parentId);
        }
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDelete(UUID id) {
        Product product = getActiveEntityById(id);
        product.setDeleted(true);
        product.setDeletedAt(Instant.now());
        product.setActive(false);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        if (saved.getParentProductId() != null) {
            productCatalogReadModelProjector.refreshParentVariationFlag(saved.getParentProductId());
        }
        productCacheVersionService.bumpAllProductReadCaches();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse restore(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (!product.isDeleted()) {
            throw new ValidationException("Product is not soft deleted: " + id);
        }
        product.setDeleted(false);
        product.setDeletedAt(null);
        product.setActive(true);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        if (saved.getParentProductId() != null) {
            productCatalogReadModelProjector.refreshParentVariationFlag(saved.getParentProductId());
        }
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void evictPublicCachesForVendorVisibilityChange(UUID vendorId) {
        // Vendor visibility state is checked at read time, but list/detail caches must be evicted
        // when vendor state changes so storefront hides/shows products immediately.
        productCacheVersionService.bumpAllProductReadCaches();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateAllByVendor(UUID vendorId) {
        int count = productRepository.deactivateAllByVendorId(vendorId);
        if (count > 0) {
            productCacheVersionService.bumpAllProductReadCaches();
        }
        return count;
    }

    private Product getActiveEntityById(UUID id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Product getActiveEntityBySlug(String slug) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (normalizedSlug.isEmpty()) {
            throw new ResourceNotFoundException("Product not found: " + slug);
        }
        return productRepository.findBySlug(normalizedSlug)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
    }

    private Product resolveProductByIdOrSlug(String idOrSlug) {
        UUID parsedId = tryParseUuid(idOrSlug);
        if (parsedId != null) {
            try {
                return getActiveEntityById(parsedId);
            } catch (ResourceNotFoundException ignored) {
                // Try slug as fallback.
            }
        }
        return getActiveEntityBySlug(idOrSlug);
    }

    private Specification<ProductCatalogRead> buildReadFilterSpec(
            String q,
            String sku,
            String category,
            String mainCategory,
            String subCategory,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice,
            boolean includeOrphanParents
    ) {
        Specification<ProductCatalogRead> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(q)) {
            String normalized = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(root.get("nameLc"), normalized),
                    cb.like(root.get("shortDescriptionLc"), normalized),
                    cb.like(root.get("descriptionLc"), normalized)
            ));
        }
        if (StringUtils.hasText(sku)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("skuLc"), sku.trim().toLowerCase(Locale.ROOT)));
        }
        if (vendorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vendorId"), vendorId));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productType"), type));
        } else {
            // Public/product listings should not include child variation rows by default.
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("productType"), ProductType.VARIATION));
        }
        if (!includeOrphanParents && (type == null || type == ProductType.PARENT)) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.notEqual(root.get("productType"), ProductType.PARENT),
                    cb.isTrue(root.get("hasActiveVariationChild"))
            ));
        }
        if (StringUtils.hasText(category)) {
            String encodedToken = "%|" + category.trim().toLowerCase(Locale.ROOT) + "|%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("categoryTokensLc"), encodedToken));
        }
        if (StringUtils.hasText(mainCategory)) {
            String normalizedMain = mainCategory.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("mainCategoryLc"), normalizedMain));
        }
        if (StringUtils.hasText(subCategory)) {
            String encodedToken = "%|" + subCategory.trim().toLowerCase(Locale.ROOT) + "|%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("subCategoryTokensLc"), encodedToken));
        }
        if (minSellingPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("sellingPrice"), minSellingPrice));
        }
        if (maxSellingPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("sellingPrice"), maxSellingPrice));
        }

        return spec;
    }

    private void applyUpsertRequest(Product product, UpsertProductRequest request, UUID parentProductId, Product parentProduct) {
        String normalizedName = request.name().trim();
        String requestedSlug = normalizeRequestedSlug(request.slug());
        boolean autoSlug = requestedSlug.isEmpty();
        String baseSlug = autoSlug ? SlugUtils.toSlug(normalizedName) : requestedSlug;
        String resolvedSlug = resolveUniqueSlug(baseSlug, product.getId(), autoSlug);
        String normalizedShortDescription = request.shortDescription().trim();
        String normalizedDescription = request.description().trim();
        String normalizedSku = request.sku().trim();

        List<String> normalizedImages = normalizeImages(request.images());
        Set<Category> resolvedCategories = request.productType() == ProductType.VARIATION
                ? resolveCategoriesFromParent(parentProduct)
                : resolveCategories(request.categories());
        List<ProductVariationAttribute> normalizedVariations = normalizeVariations(request.variations());
        UUID resolvedVendorId = resolveVendorId(request.vendorId(), request.productType(), parentProduct);

        validatePricing(request.regularPrice(), request.discountedPrice());
        validateProductTypeAndVariations(request.productType(), normalizedVariations);
        validateMainAndSubCategories(resolvedCategories);

        product.setName(normalizedName);
        product.setSlug(resolvedSlug);
        product.setShortDescription(normalizedShortDescription);
        product.setDescription(normalizedDescription);
        product.setImages(normalizedImages);
        product.setRegularPrice(request.regularPrice());
        product.setDiscountedPrice(request.discountedPrice());
        product.setVendorId(resolvedVendorId);
        product.setParentProductId(parentProductId);
        product.setCategories(resolvedCategories);
        product.setProductType(request.productType());
        product.setVariations(normalizedVariations);
        product.setSku(normalizedSku);
        product.setActive(request.active() == null || request.active());
        product.setDeleted(false);
        product.setDeletedAt(null);
    }

    public static String listCacheKey(
            Pageable pageable,
            String q,
            String sku,
            String category,
            String mainCategory,
            String subCategory,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice,
            boolean includeOrphanParents
    ) {
        return pageableCacheKey(pageable)
                + "::q=" + normalizeCacheFilter(q)
                + "::sku=" + normalizeCacheFilter(sku)
                + "::category=" + normalizeCacheFilter(category)
                + "::mainCategory=" + normalizeCacheFilter(mainCategory)
                + "::subCategory=" + normalizeCacheFilter(subCategory)
                + "::vendorId=" + (vendorId == null ? "" : vendorId)
                + "::type=" + (type == null ? "" : type.name())
                + "::minPrice=" + decimalKey(minSellingPrice)
                + "::maxPrice=" + decimalKey(maxSellingPrice)
                + "::includeOrphanParents=" + includeOrphanParents;
    }

    public static String deletedListCacheKey(
            Pageable pageable,
            String q,
            String sku,
            String category,
            String mainCategory,
            String subCategory,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice
    ) {
        return "deleted::" + listCacheKey(
                pageable,
                q,
                sku,
                category,
                mainCategory,
                subCategory,
                vendorId,
                type,
                minSellingPrice,
                maxSellingPrice,
                true
        );
    }

    private UUID resolveVendorId(UUID requestedVendorId, ProductType productType, Product parentProduct) {
        if (productType == ProductType.VARIATION) {
            if (parentProduct == null) {
                throw new ValidationException("Parent product is required for variation vendor inheritance");
            }
            UUID inheritedVendorId = parentProduct.getVendorId();
            if (inheritedVendorId == null) {
                throw new ValidationException("Parent product vendorId is required for variation creation");
            }
            if (requestedVendorId != null && !inheritedVendorId.equals(requestedVendorId)) {
                throw new ValidationException("Variation vendorId must match parent product vendorId");
            }
            return inheritedVendorId;
        }
        return requestedVendorId != null ? requestedVendorId : ADMIN_VENDOR_UUID;
    }

    private List<String> normalizeImages(List<String> images) {
        if (images.size() > 5) {
            throw new ValidationException("A product can have at most 5 images");
        }
        List<String> normalized = new ArrayList<>();
        for (String image : images) {
            String value = image.trim();
            if (!IMAGE_FILE_PATTERN.matcher(value).matches()) {
                throw new ValidationException("Invalid image name: " + image);
            }
            if (value.startsWith("/") || value.contains("..")) {
                throw new ValidationException("Invalid image name: " + image);
            }
            if (normalized.contains(value)) {
                throw new ValidationException("Duplicate image name is not allowed: " + value);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private Set<Category> resolveCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new ValidationException("category list cannot be empty");
        }
        Set<Category> resolved = new java.util.LinkedHashSet<>();
        for (String category : categories) {
            if (category == null) {
                throw new ValidationException("category cannot be blank");
            }
            String normalized = category.trim().toLowerCase();
            if (normalized.isEmpty()) {
                throw new ValidationException("category cannot be blank");
            }
            Category found = categoryRepository.findByNormalizedName(normalized)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ValidationException("Category not found or deleted: " + category));
            resolved.add(found);
        }
        return resolved;
    }

    private Set<Category> resolveCategoriesFromParent(Product parentProduct) {
        if (parentProduct == null) {
            throw new ValidationException("Parent product is required for variation category inheritance");
        }
        if (parentProduct.getProductType() != ProductType.PARENT) {
            throw new ValidationException("Variations can be added only to parent products");
        }
        return new java.util.LinkedHashSet<>(parentProduct.getCategories());
    }

    private void validateMainAndSubCategories(Set<Category> categories) {
        List<Category> parents = categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.PARENT)
                .toList();
        if (parents.size() != 1) {
            throw new ValidationException("Product must have exactly one main parent category");
        }
        UUID mainParentId = parents.getFirst().getId();
        List<Category> subs = categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.SUB)
                .toList();
        for (Category sub : subs) {
            if (sub.getParentCategoryId() == null || !sub.getParentCategoryId().equals(mainParentId)) {
                throw new ValidationException("All sub categories must belong to the selected main parent category");
            }
        }
    }

    private List<ProductVariationAttribute> normalizeVariations(List<ProductVariationAttributeRequest> variations) {
        if (variations == null || variations.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductVariationAttribute> normalized = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (ProductVariationAttributeRequest v : variations) {
            String name = v.name().trim().toLowerCase();
            if (name.isEmpty()) {
                throw new ValidationException("variation name is required");
            }
            if (!names.add(name)) {
                throw new ValidationException("duplicate variation attribute name: " + name);
            }
            String value = v.value() == null ? "" : v.value().trim();
            normalized.add(ProductVariationAttribute.builder()
                    .name(name)
                    .value(value)
                    .build());
        }
        return normalized;
    }

    private void validatePricing(BigDecimal regularPrice, BigDecimal discountedPrice) {
        if (discountedPrice == null) {
            return;
        }
        if (discountedPrice.compareTo(regularPrice) > 0) {
            throw new ValidationException("discountedPrice cannot be greater than regularPrice");
        }
    }

    private void validateProductTypeAndVariations(ProductType productType, List<ProductVariationAttribute> variations) {
        if (productType == ProductType.SINGLE && !variations.isEmpty()) {
            throw new ValidationException("variations are not allowed when productType=SINGLE");
        }
        if (productType == ProductType.PARENT && variations.isEmpty()) {
            throw new ValidationException("variation attribute names are required when productType=PARENT");
        }
        if (productType == ProductType.PARENT) {
            for (ProductVariationAttribute attribute : variations) {
                if (!attribute.getValue().isEmpty()) {
                    throw new ValidationException("parent product variation attributes should not have values");
                }
            }
        }
        if (productType == ProductType.VARIATION) {
            if (variations.isEmpty()) {
                throw new ValidationException("variations are required when productType=VARIATION");
            }
            for (ProductVariationAttribute attribute : variations) {
                if (attribute.getValue().isEmpty()) {
                    throw new ValidationException("variation value is required for attribute: " + attribute.getName());
                }
            }
        }
    }

    private void validateVariationAgainstParent(Product parent, Product variation) {
        if (!Objects.equals(parent.getVendorId(), variation.getVendorId())) {
            throw new ValidationException("Variation vendorId must match parent vendorId");
        }
        java.util.Set<String> parentNames = parent.getVariations().stream()
                .map(ProductVariationAttribute::getName)
                .collect(java.util.stream.Collectors.toSet());
        if (parentNames.isEmpty()) {
            throw new ValidationException("Parent product must define variation attributes before adding child variations");
        }
        java.util.Set<String> childNames = variation.getVariations().stream()
                .map(ProductVariationAttribute::getName)
                .collect(java.util.stream.Collectors.toSet());
        if (!childNames.equals(parentNames)) {
            throw new ValidationException("Variation attributes must match parent attributes exactly: " + parentNames);
        }

        String candidateSignature = buildVariationSignature(parentNames, variation);
        boolean duplicateExists = productRepository.findByParentProductIdAndDeletedFalseAndActiveTrue(parent.getId())
                .stream()
                .filter(existing -> existing.getProductType() == ProductType.VARIATION)
                .filter(existing -> !Objects.equals(existing.getId(), variation.getId()))
                .anyMatch(existing -> buildVariationSignature(parentNames, existing).equals(candidateSignature));
        if (duplicateExists) {
            throw new ValidationException("Variation with the same attribute values already exists for this parent product");
        }
    }

    private String buildVariationSignature(java.util.Set<String> expectedAttributeNames, Product product) {
        java.util.Map<String, String> valuesByName = new java.util.HashMap<>();
        for (ProductVariationAttribute attribute : product.getVariations()) {
            valuesByName.put(attribute.getName(), normalizeVariationValue(attribute.getValue()));
        }
        return expectedAttributeNames.stream()
                .sorted()
                .map(name -> name + "=" + valuesByName.getOrDefault(name, ""))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private String normalizeVariationValue(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String pageableCacheKey(Pageable pageable) {
        if (pageable == null) {
            return "page=0::size=20::sort=UNSORTED";
        }
        return "page=" + pageable.getPageNumber()
                + "::size=" + pageable.getPageSize()
                + "::sort=" + pageable.getSort();
    }

    private static String normalizeCacheFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String decimalKey(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private ProductResponse toPublicResponse(Product product) {
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found: " + product.getId());
        }
        assertVendorStorefrontVisible(product.getVendorId(), product.getId());
        if (product.getProductType() == ProductType.VARIATION && product.getParentProductId() != null) {
            Product parent = getActiveEntityById(product.getParentProductId());
            if (!parent.isActive()) {
                throw new ResourceNotFoundException("Product not found: " + product.getId());
            }
            assertVendorStorefrontVisible(parent.getVendorId(), product.getId());
        }
        return toResponse(product);
    }

    private Page<ProductSummaryResponse> filterHiddenVendorProducts(Page<ProductSummaryResponse> page, Pageable pageable) {
        if (page == null || page.isEmpty()) {
            return page;
        }
        Map<UUID, VendorOperationalStateResponse> states = resolveVendorStates(
                page.getContent().stream().map(ProductSummaryResponse::vendorId).toList()
        );
        List<ProductSummaryResponse> filtered = page.getContent().stream()
                .filter(row -> isVendorVisibleForStorefront(row.vendorId(), states))
                .toList();
        if (filtered.size() == page.getContent().size()) {
            return page;
        }
        long adjustedTotal = Math.min(page.getTotalElements(), pageable.getOffset() + filtered.size());
        return new PageImpl<>(filtered, pageable, adjustedTotal);
    }

    private void assertVendorStorefrontVisible(UUID vendorId, UUID productId) {
        if (vendorId == null || ADMIN_VENDOR_UUID.equals(vendorId)) {
            return;
        }
        VendorOperationalStateResponse state = vendorOperationalStateClient.getState(vendorId, requireInternalAuth());
        if (state == null || !state.storefrontVisible()) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private Map<UUID, VendorOperationalStateResponse> resolveVendorStates(Collection<UUID> vendorIds) {
        List<UUID> ids = vendorIds == null ? List.of() : vendorIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !ADMIN_VENDOR_UUID.equals(id))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return vendorOperationalStateClient.getStates(ids, requireInternalAuth());
    }

    private boolean isVendorVisibleForStorefront(UUID vendorId, Map<UUID, VendorOperationalStateResponse> states) {
        if (vendorId == null || ADMIN_VENDOR_UUID.equals(vendorId)) {
            return true;
        }
        VendorOperationalStateResponse state = states.get(vendorId);
        return state != null && state.storefrontVisible();
    }

    private String requireInternalAuth() {
        if (!StringUtils.hasText(internalAuthSharedSecret)) {
            throw new ServiceUnavailableException("INTERNAL_AUTH_SHARED_SECRET is not configured in product-service");
        }
        return internalAuthSharedSecret;
    }

    private UUID tryParseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeRequestedSlug(String slug) {
        String normalized = SlugUtils.toSlug(slug);
        return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
    }

    private String resolveUniqueSlug(String baseSlug, UUID existingId, boolean allowAutoSuffix) {
        String seed = StringUtils.hasText(baseSlug) ? baseSlug : "product";
        String normalizedSeed = seed.length() > 180 ? seed.substring(0, 180) : seed;
        if (isSlugAvailable(normalizedSeed, existingId)) {
            return normalizedSeed;
        }
        if (!allowAutoSuffix) {
            throw new ValidationException("Product slug must be unique: " + normalizedSeed);
        }
        int suffix = 2;
        while (suffix < 100_000) {
            String candidate = appendSlugSuffix(normalizedSeed, suffix, 180);
            if (isSlugAvailable(candidate, existingId)) {
                return candidate;
            }
            suffix++;
        }
        throw new ValidationException("Unable to generate a unique product slug");
    }

    private String appendSlugSuffix(String slug, int suffix, int maxLen) {
        String suffixPart = "-" + suffix;
        int allowedBaseLength = Math.max(1, maxLen - suffixPart.length());
        String base = slug.length() > allowedBaseLength ? slug.substring(0, allowedBaseLength) : slug;
        return base + suffixPart;
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getParentProductId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getDescription(),
                List.copyOf(p.getImages()),
                p.getRegularPrice(),
                p.getDiscountedPrice(),
                resolveSellingPrice(p),
                p.getVendorId(),
                resolveMainCategoryName(p.getCategories()),
                resolveMainCategorySlug(p.getCategories()),
                resolveSubCategoryNames(p.getCategories()),
                resolveSubCategorySlugs(p.getCategories()),
                p.getCategories().stream().map(Category::getName).collect(java.util.stream.Collectors.toSet()),
                p.getProductType(),
                p.getVariations().stream()
                        .map(v -> new ProductVariationAttributeResponse(v.getName(), v.getValue()))
                        .toList(),
                p.getSku(),
                p.isActive(),
                p.isDeleted(),
                p.getDeletedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private ProductSummaryResponse toSummaryResponse(ProductCatalogRead row) {
        return new ProductSummaryResponse(
                row.getId(),
                row.getSlug(),
                row.getName(),
                row.getShortDescription(),
                row.getMainImage(),
                row.getRegularPrice(),
                row.getDiscountedPrice(),
                row.getSellingPrice(),
                row.getSku(),
                row.getMainCategory(),
                decodeTokenSet(row.getSubCategoryTokens()),
                decodeTokenSet(row.getCategoryTokens()),
                row.getProductType(),
                row.getVendorId(),
                row.isActive(),
                List.of()
        );
    }

    private ProductSummaryResponse toSummaryResponse(Product p) {
        String mainImage = p.getImages().isEmpty() ? null : p.getImages().getFirst();
        return new ProductSummaryResponse(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getShortDescription(),
                mainImage,
                p.getRegularPrice(),
                p.getDiscountedPrice(),
                resolveSellingPrice(p),
                p.getSku(),
                resolveMainCategoryName(p.getCategories()),
                resolveSubCategoryNames(p.getCategories()),
                p.getCategories().stream().map(Category::getName).collect(java.util.stream.Collectors.toSet()),
                p.getProductType(),
                p.getVendorId(),
                p.isActive(),
                p.getVariations().stream()
                        .map(v -> new ProductVariationAttributeResponse(v.getName(), v.getValue()))
                        .toList()
        );
    }

    private Set<String> decodeTokenSet(String encodedTokens) {
        if (!StringUtils.hasText(encodedTokens)) {
            return Set.of();
        }
        String[] segments = encodedTokens.split("\\|");
        Set<String> values = new LinkedHashSet<>();
        for (String segment : segments) {
            if (StringUtils.hasText(segment)) {
                values.add(segment.trim());
            }
        }
        return values;
    }

    private BigDecimal resolveSellingPrice(Product p) {
        return p.getDiscountedPrice() != null ? p.getDiscountedPrice() : p.getRegularPrice();
    }

    private String resolveMainCategoryName(Set<Category> categories) {
        return categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.PARENT)
                .map(Category::getName)
                .findFirst()
                .orElse(null);
    }

    private String resolveMainCategorySlug(Set<Category> categories) {
        return categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.PARENT)
                .map(Category::getSlug)
                .findFirst()
                .orElse(null);
    }

    private Set<String> resolveSubCategoryNames(Set<Category> categories) {
        return categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.SUB)
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Set<String> resolveSubCategorySlugs(Set<Category> categories) {
        return categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.SUB)
                .map(Category::getSlug)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
