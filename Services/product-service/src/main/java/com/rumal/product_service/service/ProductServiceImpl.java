package com.rumal.product_service.service;

import com.rumal.product_service.client.InventoryClient;
import com.rumal.product_service.client.VendorOperationalStateClient;
import com.rumal.product_service.dto.StockAvailabilitySummary;
import com.rumal.product_service.dto.BulkCategoryReassignRequest;
import com.rumal.product_service.dto.BulkDeleteRequest;
import com.rumal.product_service.dto.BulkOperationResult;
import com.rumal.product_service.dto.BulkPriceUpdateRequest;
import com.rumal.product_service.dto.CsvImportResult;
import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSpecificationResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.dto.ProductVariationAttributeRequest;
import com.rumal.product_service.dto.ProductVariationAttributeResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.dto.UpsertProductSpecificationRequest;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.ProductCatalogRead;
import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductSpecification;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.entity.ProductVariationAttribute;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ServiceUnavailableException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.ProductCatalogReadRepository;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import com.rumal.product_service.repo.ProductSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    private static final Pattern IMAGE_FILE_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+\\.[A-Za-z0-9]{2,8}$");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCatalogReadRepository productCatalogReadRepository;
    private final ProductSpecificationRepository productSpecificationRepository;
    private final ProductCatalogReadModelProjector productCatalogReadModelProjector;
    private final VendorOperationalStateClient vendorOperationalStateClient;
    private final InventoryClient inventoryClient;
    private final ProductCacheVersionService productCacheVersionService;

    @Lazy
    @Autowired
    private ProductServiceImpl self;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse create(UpsertProductRequest request) {
        if (request.productType() == ProductType.VARIATION) {
            throw new ValidationException("Use /admin/products/{parentId}/variations to create variation products");
        }
        assertVendorVerified(request.vendorId());
        Product product = new Product();
        applyUpsertRequest(product, request, null, null);
        Product saved = productRepository.save(product);
        saveSpecifications(saved, request.specifications());
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
        assertVendorVerified(parent.getVendorId());

        Product variation = new Product();
        applyUpsertRequest(variation, request, parent.getId(), parent);
        validateVariationAgainstParent(parent, variation);
        Product saved = productRepository.save(variation);
        saveSpecifications(saved, request.specifications());
        productCatalogReadModelProjector.upsert(saved);
        productCatalogReadModelProjector.refreshParentVariationFlag(parent.getId());
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "@productCacheVersionService.productByIdVersion() + '::id::' + #id")
    public ProductResponse getById(UUID id) {
        Product product = getActiveEntityByIdWithDetails(id);
        return toPublicResponse(product);
    }

    @Override
    @Cacheable(cacheNames = "productById", key = "@productCacheVersionService.productByIdVersion() + '::slug::' + #slug")
    public ProductResponse getBySlug(String slug) {
        Product product = getActiveEntityBySlugWithDetails(slug);
        return toPublicResponse(product);
    }

    @Override
    public ProductResponse getByIdOrSlug(String idOrSlug) {
        UUID parsedId = tryParseUuid(idOrSlug);
        if (parsedId != null) {
            try {
                return self.getById(parsedId);
            } catch (ResourceNotFoundException ignored) {
                // Fall through to slug lookup.
            }
        }
        return self.getBySlug(idOrSlug);
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
    public List<ProductSummaryResponse> getByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Product> products = productRepository.findByIdInAndDeletedFalseAndActiveTrue(ids);
        List<ProductSummaryResponse> summaries = products.stream().map(this::toSummaryResponse).toList();
        return enrichListWithStock(summaries);
    }

    @Override
    public Page<ProductSummaryResponse> listUpdatedSince(Instant since, Pageable pageable) {
        Specification<ProductCatalogRead> spec = (root, query, cb) -> cb.and(
                cb.isFalse(root.get("deleted")),
                cb.isTrue(root.get("active")),
                cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED),
                cb.greaterThanOrEqualTo(root.get("updatedAt"), since)
        );
        return productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
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
        List<ProductSummaryResponse> result = variations.stream()
                .filter(product -> product.getProductType() == ProductType.VARIATION)
                .filter(product -> isVendorVisibleForStorefront(product.getVendorId(), states))
                .map(this::toSummaryResponse)
                .toList();
        return enrichListWithStock(result);
    }

    @Override
    @Cacheable(
            cacheNames = "productsList",
            key = "@productCacheVersionService.productsListVersion() + '::' + T(com.rumal.product_service.service.ProductServiceImpl).listCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice,#includeOrphanParents,#brand,#approvalStatus,#specs,#vendorName,#createdAfter,#createdBefore)"
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
            boolean includeOrphanParents,
            String brand,
            ApprovalStatus approvalStatus,
            Map<String, String> specs,
            String vendorName,
            Instant createdAfter,
            Instant createdBefore
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
                includeOrphanParents,
                brand,
                approvalStatus,
                vendorName,
                createdAfter,
                createdBefore
        )
                .and((root, query, cb) -> cb.isFalse(root.get("deleted")))
                .and((root, query, cb) -> cb.isTrue(root.get("active")))
                .and((root, query, cb) -> cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));

        if (specs != null && !specs.isEmpty()) {
            Set<UUID> matchingProductIds = resolveSpecFilteredProductIds(specs);
            if (matchingProductIds.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and((root, query, cb) -> root.get("id").in(matchingProductIds));
        }

        Page<ProductSummaryResponse> page = productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
        page = filterHiddenVendorProducts(page, pageable);
        return enrichPageWithStock(page, pageable);
    }

    @Override
    @Cacheable(
            cacheNames = "adminProductsList",
            key = "@productCacheVersionService.productsListVersion() + '::' + T(com.rumal.product_service.service.ProductServiceImpl).adminListCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice,#includeOrphanParents,#brand,#approvalStatus,#active)"
    )
    public Page<ProductSummaryResponse> adminList(
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
            boolean includeOrphanParents,
            String brand,
            ApprovalStatus approvalStatus,
            Boolean active
    ) {
        Specification<ProductCatalogRead> spec = buildReadFilterSpec(
                q, sku, category, mainCategory, subCategory, vendorId, type,
                minSellingPrice, maxSellingPrice, includeOrphanParents, brand,
                approvalStatus, null, null, null
        )
                .and((root, query, cb) -> cb.isFalse(root.get("deleted")));

        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        Page<ProductSummaryResponse> page = productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
        return enrichPageWithStock(page, pageable);
    }

    @Override
    @Cacheable(
            cacheNames = "deletedProductsList",
            key = "@productCacheVersionService.deletedProductsListVersion() + '::' + T(com.rumal.product_service.service.ProductServiceImpl).deletedListCacheKey(" +
                    "#pageable,#q,#sku,#category,#mainCategory,#subCategory,#vendorId,#type,#minSellingPrice,#maxSellingPrice,#brand)"
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
            BigDecimal maxSellingPrice,
            String brand
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
                true,
                brand,
                null,
                null,
                null,
                null
        )
                .and((root, query, cb) -> cb.isTrue(root.get("deleted")));
        return productCatalogReadRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse update(UUID id, UpsertProductRequest request) {
        Product product = getActiveEntityById(id);
        assertVendorVerified(product.getVendorId());
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
        saveSpecifications(saved, request.specifications());
        productCatalogReadModelProjector.upsert(saved);
        if (parentId != null) {
            productCatalogReadModelProjector.refreshParentVariationFlag(parentId);
        }
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
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
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
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
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void incrementViewCount(UUID productId) {
        productRepository.incrementViewCount(productId);
        productCatalogReadRepository.incrementViewCount(productId);
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

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse submitForReview(UUID productId) {
        Product product = getActiveEntityById(productId);
        ApprovalStatus current = product.getApprovalStatus();
        if (current != ApprovalStatus.DRAFT && current != ApprovalStatus.REJECTED) {
            throw new ValidationException("Only DRAFT or REJECTED products can be submitted for review");
        }
        product.setApprovalStatus(ApprovalStatus.PENDING_REVIEW);
        product.setRejectionReason(null);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse approveProduct(UUID productId) {
        Product product = getActiveEntityById(productId);
        if (product.getApprovalStatus() != ApprovalStatus.PENDING_REVIEW) {
            throw new ValidationException("Only PENDING_REVIEW products can be approved");
        }
        product.setApprovalStatus(ApprovalStatus.APPROVED);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ProductResponse rejectProduct(UUID productId, String reason) {
        Product product = getActiveEntityById(productId);
        if (product.getApprovalStatus() != ApprovalStatus.PENDING_REVIEW) {
            throw new ValidationException("Only PENDING_REVIEW products can be rejected");
        }
        product.setApprovalStatus(ApprovalStatus.REJECTED);
        product.setRejectionReason(reason);
        Product saved = productRepository.save(product);
        productCatalogReadModelProjector.upsert(saved);
        productCacheVersionService.evictProductById(saved.getId(), saved.getSlug());
        productCacheVersionService.bumpListCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public BulkOperationResult bulkDelete(BulkDeleteRequest request) {
        List<UUID> ids = request.productIds();
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (UUID id : ids) {
            try {
                Product product = productRepository.findById(id)
                        .filter(p -> !p.isDeleted())
                        .orElse(null);
                if (product == null) {
                    errors.add("Product not found or already deleted: " + id);
                    continue;
                }
                product.setDeleted(true);
                product.setDeletedAt(Instant.now());
                product.setActive(false);
                Product saved = productRepository.save(product);
                productCatalogReadModelProjector.upsert(saved);
                if (saved.getParentProductId() != null) {
                    productCatalogReadModelProjector.refreshParentVariationFlag(saved.getParentProductId());
                }
                success++;
            } catch (Exception ex) {
                errors.add("Failed to delete product " + id + ": " + ex.getMessage());
            }
        }
        if (success > 0) {
            productCacheVersionService.bumpAllProductReadCaches();
        }
        return new BulkOperationResult(ids.size(), success, ids.size() - success, errors);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public BulkOperationResult bulkPriceUpdate(BulkPriceUpdateRequest request) {
        List<BulkPriceUpdateRequest.PriceUpdateItem> items = request.items();
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (BulkPriceUpdateRequest.PriceUpdateItem item : items) {
            try {
                Product product = productRepository.findById(item.productId())
                        .filter(p -> !p.isDeleted())
                        .orElse(null);
                if (product == null) {
                    errors.add("Product not found: " + item.productId());
                    continue;
                }
                if (item.discountedPrice() != null && item.discountedPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("discountedPrice must be greater than 0 when set for product " + item.productId());
                    continue;
                }
                if (item.discountedPrice() != null && item.discountedPrice().compareTo(item.regularPrice()) > 0) {
                    errors.add("discountedPrice cannot be greater than regularPrice for product " + item.productId());
                    continue;
                }
                product.setRegularPrice(item.regularPrice());
                product.setDiscountedPrice(item.discountedPrice());
                Product saved = productRepository.save(product);
                productCatalogReadModelProjector.upsert(saved);
                success++;
            } catch (Exception ex) {
                errors.add("Failed to update price for product " + item.productId() + ": " + ex.getMessage());
            }
        }
        if (success > 0) {
            productCacheVersionService.bumpAllProductReadCaches();
        }
        return new BulkOperationResult(items.size(), success, items.size() - success, errors);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public BulkOperationResult bulkCategoryReassign(BulkCategoryReassignRequest request) {
        List<UUID> ids = request.productIds();
        UUID targetCategoryId = request.targetCategoryId();
        Category targetCategory = categoryRepository.findById(targetCategoryId)
                .filter(c -> !c.isDeleted())
                .orElse(null);
        if (targetCategory == null) {
            return new BulkOperationResult(ids.size(), 0, ids.size(), List.of("Target category not found or deleted: " + targetCategoryId));
        }

        int success = 0;
        List<String> errors = new ArrayList<>();
        for (UUID id : ids) {
            try {
                Product product = productRepository.findById(id)
                        .filter(p -> !p.isDeleted())
                        .orElse(null);
                if (product == null) {
                    errors.add("Product not found: " + id);
                    continue;
                }
                Set<Category> updatedCategories = new java.util.LinkedHashSet<>(product.getCategories());
                updatedCategories.add(targetCategory);
                product.setCategories(updatedCategories);
                Product saved = productRepository.save(product);
                productCatalogReadModelProjector.upsert(saved);
                success++;
            } catch (Exception ex) {
                errors.add("Failed to reassign category for product " + id + ": " + ex.getMessage());
            }
        }
        if (success > 0) {
            productCacheVersionService.bumpAllProductReadCaches();
        }
        return new BulkOperationResult(ids.size(), success, ids.size() - success, errors);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProductsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,description,regularPrice,discountedPrice,sku,vendorId,productType,digital,images,active\n");

        int page = 0;
        int pageSize = 500;
        Page<Product> productPage;
        do {
            productPage = productRepository.findAll(PageRequest.of(page, pageSize));
            for (Product p : productPage.getContent()) {
                if (p.isDeleted()) {
                    continue;
                }
                sb.append(csvEscape(p.getId().toString())).append(',');
                sb.append(csvEscape(p.getName())).append(',');
                sb.append(csvEscape(p.getDescription())).append(',');
                sb.append(p.getRegularPrice()).append(',');
                sb.append(p.getDiscountedPrice() != null ? p.getDiscountedPrice() : "").append(',');
                sb.append(csvEscape(p.getSku())).append(',');
                sb.append(p.getVendorId()).append(',');
                sb.append(p.getProductType().name()).append(',');
                sb.append(p.isDigital()).append(',');
                sb.append(csvEscape(String.join(";", p.getImages()))).append(',');
                sb.append(p.isActive());
                sb.append('\n');
            }
            page++;
        } while (productPage.hasNext());

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 60)
    public CsvImportResult importProductsCsv(InputStream csvInputStream) {
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return new CsvImportResult(0, 0, 0, List.of("CSV file is empty or missing header row"));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 8) {
                        errors.add("Row " + totalRows + ": insufficient columns (expected at least 8, got " + fields.length + ")");
                        continue;
                    }
                    String name = fields[0].trim();
                    String description = fields[1].trim();
                    String regularPriceStr = fields[2].trim();
                    String discountedPriceStr = fields[3].trim();
                    String sku = fields[4].trim();
                    String vendorIdStr = fields[5].trim();
                    String productTypeStr = fields[6].trim();
                    String digitalStr = fields.length > 7 ? fields[7].trim() : "false";
                    String imagesStr = fields.length > 8 ? fields[8].trim() : "";
                    String activeStr = fields.length > 9 ? fields[9].trim() : "true";

                    if (name.isEmpty() || description.isEmpty() || regularPriceStr.isEmpty() || sku.isEmpty()) {
                        errors.add("Row " + totalRows + ": name, description, regularPrice, and sku are required");
                        continue;
                    }

                    BigDecimal regularPrice = new BigDecimal(regularPriceStr);
                    BigDecimal discountedPrice = discountedPriceStr.isEmpty() ? null : new BigDecimal(discountedPriceStr);
                    UUID vendorId = vendorIdStr.isEmpty() ? null : UUID.fromString(vendorIdStr);
                    ProductType productType = ProductType.valueOf(productTypeStr.toUpperCase(Locale.ROOT));
                    boolean digital = Boolean.parseBoolean(digitalStr);
                    List<String> images = imagesStr.isEmpty() ? List.of() : List.of(imagesStr.split(";"));
                    boolean active = Boolean.parseBoolean(activeStr);

                    if (images.isEmpty()) {
                        errors.add("Row " + totalRows + ": at least one image is required");
                        continue;
                    }

                    Product product = new Product();
                    product.setName(name);
                    product.setSlug(resolveUniqueSlug(SlugUtils.toSlug(name), null, true));
                    product.setShortDescription(name);
                    product.setDescription(description);
                    product.setRegularPrice(regularPrice);
                    product.setDiscountedPrice(discountedPrice);
                    product.setSku(sku);
                    if (vendorId == null) {
                        errors.add("Row " + totalRows + ": vendorId is required");
                        continue;
                    }
                    product.setVendorId(vendorId);
                    product.setProductType(productType);
                    product.setDigital(digital);
                    product.setImages(new ArrayList<>(images));
                    product.setThumbnailUrl(generateThumbnailUrl(images));
                    product.setActive(active);
                    product.setApprovalStatus(ApprovalStatus.DRAFT);

                    Product saved = productRepository.save(product);
                    productCatalogReadModelProjector.upsert(saved);
                    successCount++;
                } catch (Exception ex) {
                    errors.add("Row " + totalRows + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Failed to read CSV file: " + ex.getMessage());
        }

        if (successCount > 0) {
            productCacheVersionService.bumpAllProductReadCaches();
        }
        return new CsvImportResult(totalRows, successCount, totalRows - successCount, errors);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private Product getActiveEntityById(UUID id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Product getActiveEntityByIdWithDetails(UUID id) {
        return productRepository.findByIdWithDetails(id)
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

    private Product getActiveEntityBySlugWithDetails(String slug) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (normalizedSlug.isEmpty()) {
            throw new ResourceNotFoundException("Product not found: " + slug);
        }
        return productRepository.findBySlugWithDetails(normalizedSlug)
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
            boolean includeOrphanParents,
            String brand,
            ApprovalStatus approvalStatus,
            String vendorName,
            Instant createdAfter,
            Instant createdBefore
    ) {
        Specification<ProductCatalogRead> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(q)) {
            String normalized = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(root.get("nameLc"), normalized),
                    cb.like(root.get("shortDescriptionLc"), normalized),
                    cb.like(root.get("descriptionLc"), normalized),
                    cb.like(root.get("brandNameLc"), normalized)
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
        if (StringUtils.hasText(brand)) {
            String normalizedBrand = brand.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("brandNameLc"), normalizedBrand));
        }
        if (approvalStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("approvalStatus"), approvalStatus));
        }
        // Gap 3: Vendor name search (ILIKE via lowercase)
        if (StringUtils.hasText(vendorName)) {
            String normalizedVendorName = "%" + vendorName.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("vendorNameLc"), normalizedVendorName));
        }
        // Gap 6: Date range filter on products
        if (createdAfter != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
        }
        if (createdBefore != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
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
        product.setBrandName(request.brandName() != null ? request.brandName().trim() : null);
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
        product.setWeightGrams(request.weightGrams());
        product.setLengthCm(request.lengthCm());
        product.setWidthCm(request.widthCm());
        product.setHeightCm(request.heightCm());
        product.setMetaTitle(request.metaTitle() != null ? request.metaTitle().trim() : null);
        product.setMetaDescription(request.metaDescription() != null ? request.metaDescription().trim() : null);
        boolean isDigital = request.digital() != null && request.digital();
        product.setDigital(isDigital);
        if (isDigital) {
            product.setWeightGrams(null);
            product.setLengthCm(null);
            product.setWidthCm(null);
            product.setHeightCm(null);
        }
        product.setBundledProductIds(request.bundledProductIds() != null ? new ArrayList<>(request.bundledProductIds()) : new ArrayList<>());
        product.setThumbnailUrl(generateThumbnailUrl(normalizedImages));
        product.setDeleted(false);
        product.setDeletedAt(null);
    }

    private String generateThumbnailUrl(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        String firstImage = images.getFirst();
        int lastDot = firstImage.lastIndexOf('.');
        if (lastDot <= 0) {
            return firstImage + "-thumb";
        }
        return firstImage.substring(0, lastDot) + "-thumb" + firstImage.substring(lastDot);
    }

    private void saveSpecifications(Product product, List<UpsertProductSpecificationRequest> specifications) {
        productSpecificationRepository.deleteByProductId(product.getId());
        if (specifications == null || specifications.isEmpty()) {
            return;
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        List<ProductSpecification> entities = new ArrayList<>();
        for (int i = 0; i < specifications.size(); i++) {
            UpsertProductSpecificationRequest spec = specifications.get(i);
            String normalizedKey = spec.key().trim().toLowerCase(Locale.ROOT);
            if (normalizedKey.isEmpty()) {
                throw new ValidationException("specification key is required");
            }
            if (!keys.add(normalizedKey)) {
                throw new ValidationException("duplicate specification key: " + normalizedKey);
            }
            String value = spec.value().trim();
            if (value.isEmpty()) {
                throw new ValidationException("specification value is required for key: " + normalizedKey);
            }
            entities.add(ProductSpecification.builder()
                    .product(product)
                    .attributeKey(normalizedKey)
                    .attributeValue(value)
                    .displayOrder(spec.displayOrder() != null ? spec.displayOrder() : i)
                    .build());
        }
        productSpecificationRepository.saveAll(entities);
    }

    private Set<UUID> resolveSpecFilteredProductIds(Map<String, String> specs) {
        Set<UUID> result = null;
        for (Map.Entry<String, String> entry : specs.entrySet()) {
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String value = entry.getValue().trim();
            Set<UUID> ids;
            if (result == null) {
                ids = productRepository.findProductIdsBySpecification(key, value);
            } else {
                ids = productRepository.findProductIdsBySpecificationAndProductIdIn(key, value, result);
            }
            result = ids;
            if (result.isEmpty()) {
                return result;
            }
        }
        return result != null ? result : Set.of();
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
            boolean includeOrphanParents,
            String brand,
            ApprovalStatus approvalStatus,
            Map<String, String> specs,
            String vendorName,
            Instant createdAfter,
            Instant createdBefore
    ) {
        String specsKey = specs == null || specs.isEmpty() ? "" : specs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(","));
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
                + "::includeOrphanParents=" + includeOrphanParents
                + "::brand=" + normalizeCacheFilter(brand)
                + "::approvalStatus=" + (approvalStatus == null ? "" : approvalStatus.name())
                + "::specs=" + specsKey
                + "::vendorName=" + normalizeCacheFilter(vendorName)
                + "::createdAfter=" + (createdAfter == null ? "" : createdAfter)
                + "::createdBefore=" + (createdBefore == null ? "" : createdBefore);
    }

    public static String adminListCacheKey(
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
            boolean includeOrphanParents,
            String brand,
            ApprovalStatus approvalStatus,
            Boolean active
    ) {
        return "admin::" + pageableCacheKey(pageable)
                + "::q=" + normalizeCacheFilter(q)
                + "::sku=" + normalizeCacheFilter(sku)
                + "::category=" + normalizeCacheFilter(category)
                + "::mainCategory=" + normalizeCacheFilter(mainCategory)
                + "::subCategory=" + normalizeCacheFilter(subCategory)
                + "::vendorId=" + (vendorId == null ? "" : vendorId)
                + "::type=" + (type == null ? "" : type.name())
                + "::minPrice=" + decimalKey(minSellingPrice)
                + "::maxPrice=" + decimalKey(maxSellingPrice)
                + "::includeOrphanParents=" + includeOrphanParents
                + "::brand=" + normalizeCacheFilter(brand)
                + "::approvalStatus=" + (approvalStatus == null ? "" : approvalStatus.name())
                + "::active=" + (active == null ? "" : active);
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
            BigDecimal maxSellingPrice,
            String brand
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
                true,
                brand,
                null,
                null,
                null,
                null,
                null
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
        if (requestedVendorId == null) {
            throw new ValidationException("vendorId is required");
        }
        return requestedVendorId;
    }

    private List<String> normalizeImages(List<String> images) {
        if (images.size() > 10) {
            throw new ValidationException("A product can have at most 10 images");
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
        // Gap 7: Zero discounted price guard
        if (discountedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("discountedPrice must be greater than 0 when set");
        }
        if (discountedPrice.compareTo(regularPrice) > 0) {
            throw new ValidationException("discountedPrice cannot be greater than regularPrice");
        }
    }

    private void validateProductTypeAndVariations(ProductType productType, List<ProductVariationAttribute> variations) {
        if (productType == ProductType.SINGLE && !variations.isEmpty()) {
            throw new ValidationException("variations are not allowed when productType=SINGLE");
        }
        if (productType == ProductType.DIGITAL && !variations.isEmpty()) {
            throw new ValidationException("variations are not allowed when productType=DIGITAL");
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
        variation.setVariationSignature(candidateSignature);
        UUID excludeId = variation.getId() != null ? variation.getId() : new UUID(0, 0);
        boolean duplicateExists = productRepository.existsByParentProductIdAndDeletedFalseAndActiveTrueAndVariationSignatureAndIdNot(
                parent.getId(), candidateSignature, excludeId);
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
        ProductResponse response = toResponse(product);
        return enrichWithStock(response);
    }

    private ProductResponse enrichWithStock(ProductResponse response) {
        StockAvailabilitySummary stock = inventoryClient.getStockSummary(response.id());
        if (stock == null) return response;
        return response.withStock(stock.totalAvailable(), stock.stockStatus(), stock.backorderable());
    }

    private Page<ProductSummaryResponse> enrichPageWithStock(Page<ProductSummaryResponse> page, Pageable pageable) {
        if (page == null || page.isEmpty()) return page;
        List<UUID> productIds = page.getContent().stream().map(ProductSummaryResponse::id).toList();
        Map<UUID, StockAvailabilitySummary> stockMap = fetchBatchStockMap(productIds);
        if (stockMap.isEmpty()) return page;
        List<ProductSummaryResponse> enriched = page.getContent().stream()
                .map(p -> {
                    StockAvailabilitySummary s = stockMap.get(p.id());
                    return s != null ? p.withStock(s.totalAvailable(), s.stockStatus(), s.backorderable()) : p;
                })
                .toList();
        return new PageImpl<>(enriched, pageable, page.getTotalElements());
    }

    private List<ProductSummaryResponse> enrichListWithStock(List<ProductSummaryResponse> items) {
        if (items == null || items.isEmpty()) return items;
        List<UUID> productIds = items.stream().map(ProductSummaryResponse::id).toList();
        Map<UUID, StockAvailabilitySummary> stockMap = fetchBatchStockMap(productIds);
        if (stockMap.isEmpty()) return items;
        return items.stream()
                .map(p -> {
                    StockAvailabilitySummary s = stockMap.get(p.id());
                    return s != null ? p.withStock(s.totalAvailable(), s.stockStatus(), s.backorderable()) : p;
                })
                .toList();
    }

    private Map<UUID, StockAvailabilitySummary> fetchBatchStockMap(List<UUID> productIds) {
        List<StockAvailabilitySummary> summaries = inventoryClient.getBatchStockSummary(productIds);
        if (summaries == null || summaries.isEmpty()) return Map.of();
        return summaries.stream()
                .filter(s -> s.productId() != null)
                .collect(java.util.stream.Collectors.toMap(StockAvailabilitySummary::productId, s -> s, (a, b) -> a));
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

    private void assertVendorVerified(UUID vendorId) {
        if (vendorId == null) {
            return;
        }
        VendorOperationalStateResponse state = vendorOperationalStateClient.getState(vendorId, requireInternalAuth());
        if (state == null || !state.verified()) {
            throw new ValidationException("Vendor is not verified. Products cannot be created or updated until the vendor is verified by platform admin.");
        }
    }

    private void assertVendorStorefrontVisible(UUID vendorId, UUID productId) {
        if (vendorId == null) {
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
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return vendorOperationalStateClient.getStates(ids, requireInternalAuth());
    }

    private boolean isVendorVisibleForStorefront(UUID vendorId, Map<UUID, VendorOperationalStateResponse> states) {
        if (vendorId == null) {
            return true;
        }
        if (states.isEmpty()) {
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
        List<ProductSpecificationResponse> specifications = productSpecificationRepository
                .findByProductIdOrderByDisplayOrderAsc(p.getId())
                .stream()
                .map(s -> new ProductSpecificationResponse(s.getAttributeKey(), s.getAttributeValue(), s.getDisplayOrder()))
                .toList();
        return new ProductResponse(
                p.getId(),
                p.getParentProductId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getDescription(),
                p.getBrandName(),
                List.copyOf(p.getImages()),
                p.getThumbnailUrl(),
                p.getRegularPrice(),
                p.getDiscountedPrice(),
                resolveSellingPrice(p),
                p.getVendorId(),
                resolveMainCategoryName(p.getCategories()),
                resolveMainCategorySlug(p.getCategories()),
                resolveSubCategoryNames(p.getCategories()),
                resolveSubCategorySlugs(p.getCategories()),
                p.getCategories().stream().map(Category::getName).collect(java.util.stream.Collectors.toSet()),
                p.getCategories().stream().map(Category::getId).toList(),
                p.getProductType(),
                p.isDigital(),
                p.getVariations().stream()
                        .map(v -> new ProductVariationAttributeResponse(v.getName(), v.getValue()))
                        .toList(),
                p.getSku(),
                p.getWeightGrams(),
                p.getLengthCm(),
                p.getWidthCm(),
                p.getHeightCm(),
                p.getMetaTitle(),
                p.getMetaDescription(),
                p.getApprovalStatus(),
                p.getRejectionReason(),
                specifications,
                p.getBundledProductIds() != null ? List.copyOf(p.getBundledProductIds()) : List.of(),
                p.getViewCount(),
                p.getSoldCount(),
                p.isActive(),
                p.isDeleted(),
                p.getDeletedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                null,
                null,
                null
        );
    }

    private ProductSummaryResponse toSummaryResponse(ProductCatalogRead row) {
        return new ProductSummaryResponse(
                row.getId(),
                row.getSlug(),
                row.getName(),
                row.getShortDescription(),
                row.getBrandName(),
                row.getMainImage(),
                row.getRegularPrice(),
                row.getDiscountedPrice(),
                row.getSellingPrice(),
                row.getSku(),
                row.getMainCategory(),
                decodeTokenSet(row.getSubCategoryTokens()),
                decodeTokenSet(row.getCategoryTokens()),
                row.getProductType(),
                row.getApprovalStatus(),
                row.getVendorId(),
                row.getViewCount(),
                row.getSoldCount(),
                row.isActive(),
                List.of(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                null,
                null,
                null
        );
    }

    private ProductSummaryResponse toSummaryResponse(Product p) {
        String mainImage = p.getImages().isEmpty() ? null : p.getImages().getFirst();
        return new ProductSummaryResponse(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getShortDescription(),
                p.getBrandName(),
                mainImage,
                p.getRegularPrice(),
                p.getDiscountedPrice(),
                resolveSellingPrice(p),
                p.getSku(),
                resolveMainCategoryName(p.getCategories()),
                resolveSubCategoryNames(p.getCategories()),
                p.getCategories().stream().map(Category::getName).collect(java.util.stream.Collectors.toSet()),
                p.getProductType(),
                p.getApprovalStatus(),
                p.getVendorId(),
                p.getViewCount(),
                p.getSoldCount(),
                p.isActive(),
                p.getVariations().stream()
                        .map(v -> new ProductVariationAttributeResponse(v.getName(), v.getValue()))
                        .toList(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                null,
                null,
                null
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
