package com.rumal.product_service.service;

import com.rumal.product_service.dto.BulkCategoryReassignRequest;
import com.rumal.product_service.dto.BulkDeleteRequest;
import com.rumal.product_service.dto.BulkOperationResult;
import com.rumal.product_service.dto.BulkPriceUpdateRequest;
import com.rumal.product_service.dto.CsvImportResult;
import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ProductService {
    ProductResponse create(UpsertProductRequest request);
    ProductResponse createVariation(UUID parentId, UpsertProductRequest request);
    ProductResponse getById(UUID id);
    ProductResponse getBySlug(String slug);
    ProductResponse getByIdOrSlug(String idOrSlug);
    boolean isSlugAvailable(String slug, UUID excludeId);
    List<ProductSummaryResponse> listVariations(UUID parentId);
    List<ProductSummaryResponse> listVariationsByIdOrSlug(String parentIdOrSlug);
    Page<ProductSummaryResponse> list(Pageable pageable, String q, String sku, String category, String mainCategory, String subCategory, UUID vendorId, ProductType type, BigDecimal minSellingPrice, BigDecimal maxSellingPrice, boolean includeOrphanParents, String brand, ApprovalStatus approvalStatus, Map<String, String> specs, String vendorName, Instant createdAfter, Instant createdBefore);
    Page<ProductSummaryResponse> adminList(Pageable pageable, String q, String sku, String category, String mainCategory, String subCategory, UUID vendorId, ProductType type, BigDecimal minSellingPrice, BigDecimal maxSellingPrice, boolean includeOrphanParents, String brand, ApprovalStatus approvalStatus, Boolean active);
    Page<ProductSummaryResponse> listDeleted(Pageable pageable, String q, String sku, String category, String mainCategory, String subCategory, UUID vendorId, ProductType type, BigDecimal minSellingPrice, BigDecimal maxSellingPrice, String brand);
    void incrementViewCount(UUID productId);
    ProductResponse update(UUID id, UpsertProductRequest request);
    void softDelete(UUID id);
    ProductResponse restore(UUID id);
    void evictPublicCachesForVendorVisibilityChange(UUID vendorId);
    int deactivateAllByVendor(UUID vendorId);
    ProductResponse submitForReview(UUID productId);
    ProductResponse approveProduct(UUID productId);
    ProductResponse rejectProduct(UUID productId, String reason);
    BulkOperationResult bulkDelete(BulkDeleteRequest request, Set<UUID> allowedVendorIds);
    BulkOperationResult bulkPriceUpdate(BulkPriceUpdateRequest request, Set<UUID> allowedVendorIds);
    BulkOperationResult bulkCategoryReassign(BulkCategoryReassignRequest request, Set<UUID> allowedVendorIds);
    List<ProductSummaryResponse> getByIds(List<UUID> ids);
    List<ProductResponse> getFullDetailsByIds(List<UUID> ids);
    Page<ProductSummaryResponse> listUpdatedSince(Instant since, Pageable pageable);
    byte[] exportProductsCsv();
    CsvImportResult importProductsCsv(InputStream csvInputStream);
}
