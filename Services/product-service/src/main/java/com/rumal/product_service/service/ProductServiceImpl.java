package com.rumal.product_service.service;

import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.ProductVariationAttributeRequest;
import com.rumal.product_service.dto.ProductVariationAttributeResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.entity.ProductVariationAttribute;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final UUID ADMIN_VENDOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+\\.[A-Za-z0-9]{2,8}$");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
    })
    public ProductResponse create(UpsertProductRequest request) {
        if (request.productType() == ProductType.VARIATION) {
            throw new ValidationException("Use /admin/products/{parentId}/variations to create variation products");
        }
        Product product = new Product();
        applyUpsertRequest(product, request, null);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
    })
    public ProductResponse createVariation(UUID parentId, UpsertProductRequest request) {
        Product parent = getActiveEntityById(parentId);
        if (parent.getProductType() != ProductType.PARENT) {
            throw new ValidationException("Variations can be added only to parent products");
        }
        if (request.productType() != ProductType.VARIATION) {
            throw new ValidationException("Variation endpoint requires productType=VARIATION");
        }

        Product variation = new Product();
        applyUpsertRequest(variation, request, parent.getId());
        validateVariationAgainstParent(parent, variation);
        return toResponse(productRepository.save(variation));
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "#id")
    public ProductResponse getById(UUID id) {
        Product product = getActiveEntityById(id);
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        if (product.getProductType() == ProductType.VARIATION && product.getParentProductId() != null) {
            Product parent = getActiveEntityById(product.getParentProductId());
            if (!parent.isActive()) {
                throw new ResourceNotFoundException("Product not found: " + id);
            }
        }
        return toResponse(product);
    }

    @Override
    @Cacheable(cacheNames = "productsList", key = "'variations::' + #parentId")
    public List<ProductSummaryResponse> listVariations(UUID parentId) {
        Product parent = getActiveEntityById(parentId);
        if (parent.getProductType() != ProductType.PARENT) {
            throw new ValidationException("Variations are available only for parent products");
        }
        if (!parent.isActive()) {
            throw new ResourceNotFoundException("Product not found: " + parentId);
        }
        return productRepository.findByParentProductIdAndDeletedFalseAndActiveTrue(parentId)
                .stream()
                .filter(product -> product.getProductType() == ProductType.VARIATION)
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Cacheable(
            cacheNames = "productsList",
            key = "T(com.rumal.product_service.service.ProductServiceImpl).listCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice)"
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
            BigDecimal maxSellingPrice
    ) {
        Specification<Product> spec = buildFilterSpec(q, sku, category, mainCategory, subCategory, vendorId, type, minSellingPrice, maxSellingPrice)
                .and((root, query, cb) -> cb.isFalse(root.get("deleted")))
                .and((root, query, cb) -> cb.isTrue(root.get("active")));
        return productRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Cacheable(
            cacheNames = "deletedProductsList",
            key = "T(com.rumal.product_service.service.ProductServiceImpl).deletedListCacheKey(" +
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
        Specification<Product> spec = buildFilterSpec(q, sku, category, mainCategory, subCategory, vendorId, type, minSellingPrice, maxSellingPrice)
                .and((root, query, cb) -> cb.isTrue(root.get("deleted")));
        return productRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", key = "#id")
    })
    public ProductResponse update(UUID id, UpsertProductRequest request) {
        Product product = getActiveEntityById(id);
        UUID parentId = product.getParentProductId();
        if (parentId != null && request.productType() != ProductType.VARIATION) {
            throw new ValidationException("Existing variation product must keep productType=VARIATION");
        }
        if (parentId == null && request.productType() == ProductType.VARIATION) {
            throw new ValidationException("Use variation endpoint to create variation products under a parent");
        }
        applyUpsertRequest(product, request, parentId);
        if (parentId != null) {
            Product parent = getActiveEntityById(parentId);
            validateVariationAgainstParent(parent, product);
        }
        return toResponse(productRepository.save(product));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", key = "#id")
    })
    public void softDelete(UUID id) {
        Product product = getActiveEntityById(id);
        product.setDeleted(true);
        product.setDeletedAt(Instant.now());
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", key = "#id")
    })
    public ProductResponse restore(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (!product.isDeleted()) {
            throw new ValidationException("Product is not soft deleted: " + id);
        }
        product.setDeleted(false);
        product.setDeletedAt(null);
        product.setActive(true);
        return toResponse(productRepository.save(product));
    }

    private Product getActiveEntityById(UUID id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Specification<Product> buildFilterSpec(
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
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(q)) {
            String normalized = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), normalized),
                    cb.like(cb.lower(root.get("shortDescription")), normalized),
                    cb.like(cb.lower(root.get("description")), normalized)
            ));
        }
        if (StringUtils.hasText(sku)) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("sku")), sku.trim().toLowerCase()));
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
        if (StringUtils.hasText(category)) {
            String normalizedCategory = category.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(cb.lower(root.joinSet("categories", JoinType.LEFT).get("name")), normalizedCategory);
            });
        }
        if (StringUtils.hasText(mainCategory)) {
            String normalizedMain = mainCategory.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                var categoryJoin = root.joinSet("categories", JoinType.LEFT);
                return cb.and(
                        cb.equal(categoryJoin.get("type"), com.rumal.product_service.entity.CategoryType.PARENT),
                        cb.equal(cb.lower(categoryJoin.get("name")), normalizedMain)
                );
            });
        }
        if (StringUtils.hasText(subCategory)) {
            String normalizedSub = subCategory.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                var categoryJoin = root.joinSet("categories", JoinType.LEFT);
                return cb.and(
                        cb.equal(categoryJoin.get("type"), com.rumal.product_service.entity.CategoryType.SUB),
                        cb.equal(cb.lower(categoryJoin.get("name")), normalizedSub)
                );
            });
        }
        if (minSellingPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(
                    cb.coalesce(root.get("discountedPrice"), root.get("regularPrice")),
                    minSellingPrice
            ));
        }
        if (maxSellingPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(
                    cb.coalesce(root.get("discountedPrice"), root.get("regularPrice")),
                    maxSellingPrice
            ));
        }

        return spec;
    }

    private void applyUpsertRequest(Product product, UpsertProductRequest request, UUID parentProductId) {
        String normalizedName = request.name().trim();
        String normalizedShortDescription = request.shortDescription().trim();
        String normalizedDescription = request.description().trim();
        String normalizedSku = request.sku().trim();

        List<String> normalizedImages = normalizeImages(request.images());
        Set<Category> resolvedCategories = resolveCategories(request.categories());
        List<ProductVariationAttribute> normalizedVariations = normalizeVariations(request.variations());
        UUID resolvedVendorId = resolveVendorId(request.vendorId());

        validatePricing(request.regularPrice(), request.discountedPrice());
        validateProductTypeAndVariations(request.productType(), normalizedVariations);
        validateMainAndSubCategories(resolvedCategories);

        product.setName(normalizedName);
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
            BigDecimal maxSellingPrice
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
                + "::maxPrice=" + decimalKey(maxSellingPrice);
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
                maxSellingPrice
        );
    }

    private UUID resolveVendorId(UUID vendorId) {
        return vendorId != null ? vendorId : ADMIN_VENDOR_UUID;
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
        Set<Category> resolved = new java.util.LinkedHashSet<>();
        for (String category : categories) {
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
            boolean hasAtLeastOneValue = false;
            for (ProductVariationAttribute attribute : variations) {
                if (!attribute.getValue().isEmpty()) {
                    hasAtLeastOneValue = true;
                }
            }
            if (!hasAtLeastOneValue) {
                throw new ValidationException("At least one variation attribute value is required when productType=VARIATION");
            }
        }
    }

    private void validateVariationAgainstParent(Product parent, Product variation) {
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
                .filter(existing -> !java.util.Objects.equals(existing.getId(), variation.getId()))
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

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getParentProductId(),
                p.getName(),
                p.getShortDescription(),
                p.getDescription(),
                List.copyOf(p.getImages()),
                p.getRegularPrice(),
                p.getDiscountedPrice(),
                resolveSellingPrice(p),
                p.getVendorId(),
                resolveMainCategoryName(p.getCategories()),
                resolveSubCategoryNames(p.getCategories()),
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

    private ProductSummaryResponse toSummaryResponse(Product p) {
        String mainImage = p.getImages().isEmpty() ? null : p.getImages().getFirst();
        return new ProductSummaryResponse(
                p.getId(),
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

    private Set<String> resolveSubCategoryNames(Set<Category> categories) {
        return categories.stream()
                .filter(c -> c.getType() == com.rumal.product_service.entity.CategoryType.SUB)
                .map(Category::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
