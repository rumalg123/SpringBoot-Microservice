package com.rumal.product_service.service;

import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.UpsertProductRequest;
import com.rumal.product_service.entity.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse create(UpsertProductRequest request);
    ProductResponse createVariation(UUID parentId, UpsertProductRequest request);
    ProductResponse getById(UUID id);
    List<ProductSummaryResponse> listVariations(UUID parentId);
    Page<ProductSummaryResponse> list(Pageable pageable, String q, String sku, String category, UUID vendorId, ProductType type, BigDecimal minSellingPrice, BigDecimal maxSellingPrice);
    Page<ProductSummaryResponse> listDeleted(Pageable pageable, String q, String sku, String category, UUID vendorId, ProductType type, BigDecimal minSellingPrice, BigDecimal maxSellingPrice);
    ProductResponse update(UUID id, UpsertProductRequest request);
    void softDelete(UUID id);
    ProductResponse restore(UUID id);
}
