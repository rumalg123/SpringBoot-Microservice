package com.rumal.product_service.service;

import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.ProductVariationAttributeRequest;
import com.rumal.product_service.dto.ProductVariationAttributeResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.entity.ProductVariationAttribute;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ValidationException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final UUID ADMIN_VENDOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile("^[^\\\\/:*?\"<>|\\s]+\\.[A-Za-z0-9]{2,8}$");

    private final ProductRepository productRepository;

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
        return toResponse(productRepository.save(variation));
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "#id")
    public ProductResponse getById(UUID id) {
        Product product = getActiveEntityById(id);
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found: " + id);
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
        return productRepository.findByParentProductIdAndDeletedFalseAndActiveTrue(parentId)
                .stream()
                .filter(product -> product.getProductType() == ProductType.VARIATION)
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Cacheable(
            cacheNames = "productsList",
            key = "'p=' + #pageable.pageNumber + '::s=' + #pageable.pageSize + '::sort=' + #pageable.sort.toString() + '::q=' + #q + '::sku=' + #sku + '::cat=' + #category + '::vendor=' + #vendorId + '::type=' + #type + '::min=' + #minSellingPrice + '::max=' + #maxSellingPrice"
    )
    public Page<ProductSummaryResponse> list(
            Pageable pageable,
            String q,
            String sku,
            String category,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice
    ) {
        Specification<Product> spec = buildFilterSpec(q, sku, category, vendorId, type, minSellingPrice, maxSellingPrice)
                .and((root, query, cb) -> cb.isFalse(root.get("deleted")))
                .and((root, query, cb) -> cb.isTrue(root.get("active")));
        return productRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Cacheable(
            cacheNames = "deletedProductsList",
            key = "'p=' + #pageable.pageNumber + '::s=' + #pageable.pageSize + '::sort=' + #pageable.sort.toString() + '::q=' + #q + '::sku=' + #sku + '::cat=' + #category + '::vendor=' + #vendorId + '::type=' + #type + '::min=' + #minSellingPrice + '::max=' + #maxSellingPrice"
    )
    public Page<ProductSummaryResponse> listDeleted(
            Pageable pageable,
            String q,
            String sku,
            String category,
            UUID vendorId,
            ProductType type,
            BigDecimal minSellingPrice,
            BigDecimal maxSellingPrice
    ) {
        Specification<Product> spec = buildFilterSpec(q, sku, category, vendorId, type, minSellingPrice, maxSellingPrice)
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
        }
        if (StringUtils.hasText(category)) {
            String normalizedCategory = category.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(cb.lower(root.joinSet("categories", JoinType.LEFT)), normalizedCategory);
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
        Set<String> normalizedCategories = normalizeCategories(request.categories());
        List<ProductVariationAttribute> normalizedVariations = normalizeVariations(request.variations());
        UUID resolvedVendorId = resolveVendorId(request.vendorId());

        validatePricing(request.regularPrice(), request.discountedPrice());
        validateProductTypeAndVariations(request.productType(), normalizedVariations);

        product.setName(normalizedName);
        product.setShortDescription(normalizedShortDescription);
        product.setDescription(normalizedDescription);
        product.setImages(normalizedImages);
        product.setRegularPrice(request.regularPrice());
        product.setDiscountedPrice(request.discountedPrice());
        product.setVendorId(resolvedVendorId);
        product.setParentProductId(parentProductId);
        product.setCategories(normalizedCategories);
        product.setProductType(request.productType());
        product.setVariations(normalizedVariations);
        product.setSku(normalizedSku);
        product.setActive(request.active() == null || request.active());
        product.setDeleted(false);
        product.setDeletedAt(null);
    }

    private UUID resolveVendorId(UUID vendorId) {
        return vendorId != null ? vendorId : ADMIN_VENDOR_UUID;
    }

    private List<String> normalizeImages(List<String> images) {
        List<String> normalized = new ArrayList<>();
        for (String image : images) {
            String value = image.trim();
            if (!IMAGE_FILE_PATTERN.matcher(value).matches()) {
                throw new ValidationException("Invalid image name: " + image);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private Set<String> normalizeCategories(Set<String> categories) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            String value = category.trim().toLowerCase();
            if (value.isEmpty()) {
                throw new ValidationException("category cannot be blank");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private List<ProductVariationAttribute> normalizeVariations(List<ProductVariationAttributeRequest> variations) {
        if (variations == null || variations.isEmpty()) {
            return new ArrayList<>();
        }
        return variations.stream()
                .map(v -> ProductVariationAttribute.builder()
                        .name(v.name().trim().toLowerCase())
                        .value(v.value().trim())
                        .build())
                .toList();
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
        if (productType == ProductType.VARIATION && variations.isEmpty()) {
            throw new ValidationException("variations are required when productType=VARIATION");
        }
        if (productType != ProductType.VARIATION && !variations.isEmpty()) {
            throw new ValidationException("variations are allowed only when productType=VARIATION");
        }
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
                Set.copyOf(p.getCategories()),
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
                Set.copyOf(p.getCategories()),
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
}
