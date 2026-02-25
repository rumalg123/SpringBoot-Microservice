package com.rumal.product_service.controller;

import com.rumal.product_service.dto.BulkCategoryReassignRequest;
import com.rumal.product_service.dto.BulkDeleteRequest;
import com.rumal.product_service.dto.BulkOperationResult;
import com.rumal.product_service.dto.BulkPriceUpdateRequest;
import com.rumal.product_service.dto.CsvImportResult;
import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.RejectProductRequest;
import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.dto.ProductImageNameRequest;
import com.rumal.product_service.dto.ProductImageUploadResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.AdminProductAccessScopeService;
import com.rumal.product_service.service.ProductService;
import com.rumal.product_service.storage.ImageUploadRateLimiter;
import com.rumal.product_service.storage.ProductImageStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final AdminProductAccessScopeService adminProductAccessScopeService;
    private final ProductImageStorageService productImageStorageService;
    private final ImageUploadRateLimiter imageUploadRateLimiter;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<ProductSummaryResponse> listActive(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minSellingPrice,
            @RequestParam(required = false) BigDecimal maxSellingPrice,
            @RequestParam(defaultValue = "false") boolean includeOrphanParents,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        UUID scopedVendorId = adminProductAccessScopeService.resolveScopedVendorFilter(userSub, userRoles, vendorId, internalAuth);
        return productService.adminList(pageable, q, sku, category, mainCategory, subCategory, scopedVendorId, type, minSellingPrice, maxSellingPrice, includeOrphanParents, brand, approvalStatus, active);
    }

    @GetMapping("/deleted")
    public Page<ProductSummaryResponse> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minSellingPrice,
            @RequestParam(required = false) BigDecimal maxSellingPrice,
            @RequestParam(required = false) String brand,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        UUID scopedVendorId = adminProductAccessScopeService.resolveScopedVendorFilter(userSub, userRoles, vendorId, internalAuth);
        return productService.listDeleted(pageable, q, sku, category, mainCategory, subCategory, scopedVendorId, type, minSellingPrice, maxSellingPrice, brand);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertProductRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        UpsertProductRequest scopedRequest = adminProductAccessScopeService.scopeCreateRequest(userSub, userRoles, request, internalAuth);
        return productService.create(scopedRequest);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageUploadResponse uploadImages(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam("files") java.util.List<MultipartFile> files,
            @RequestParam(value = "keys", required = false) java.util.List<String> keys
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        imageUploadRateLimiter.checkRateLimit(userSub, files != null ? files.size() : 0);
        return new ProductImageUploadResponse(productImageStorageService.uploadImages(files, keys));
    }

    @PostMapping("/images/names")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageUploadResponse generateImageNames(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody ProductImageNameRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return new ProductImageUploadResponse(productImageStorageService.generateImageNames(request.fileNames()));
    }

    @PostMapping("/{parentId}/variations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createVariation(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID parentId,
            @Valid @RequestBody UpsertProductRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        adminProductAccessScopeService.assertCanManageProduct(parentId, userSub, userRoles, internalAuth);
        UpsertProductRequest scopedRequest = adminProductAccessScopeService.scopeCreateRequest(userSub, userRoles, request, internalAuth);
        return productService.createVariation(parentId, scopedRequest);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertProductRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        UpsertProductRequest scopedRequest = adminProductAccessScopeService.scopeUpdateRequest(id, userSub, userRoles, request, internalAuth);
        return productService.update(id, scopedRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProduct(id, userSub, userRoles, internalAuth);
        productService.softDelete(id);
    }

    @PostMapping("/{id}/restore")
    public ProductResponse restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProduct(id, userSub, userRoles, internalAuth);
        return productService.restore(id);
    }

    @PostMapping("/{id}/submit-for-review")
    public ProductResponse submitForReview(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProduct(id, userSub, userRoles, internalAuth);
        return productService.submitForReview(id);
    }

    @PostMapping("/{id}/approve")
    public ProductResponse approve(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.approveProduct(id);
    }

    @PostMapping("/{id}/reject")
    public ProductResponse reject(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody RejectProductRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.rejectProduct(id, request.reason());
    }

    @PostMapping("/bulk-delete")
    public BulkOperationResult bulkDelete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody BulkDeleteRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.bulkDelete(request);
    }

    @PostMapping("/bulk-price-update")
    public BulkOperationResult bulkPriceUpdate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody BulkPriceUpdateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.bulkPriceUpdate(request);
    }

    @PostMapping("/bulk-category-reassign")
    public BulkOperationResult bulkCategoryReassign(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody BulkCategoryReassignRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.bulkCategoryReassign(request);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "csv") String format
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        byte[] csvBytes = productService.exportProductsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=products.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importCsv(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        internalRequestVerifier.verify(internalAuth);
        adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
        return productService.importProductsCsv(file.getInputStream());
    }
}
