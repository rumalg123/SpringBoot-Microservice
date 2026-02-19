package com.rumal.product_service.controller;

import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @GetMapping("/{id}/variations")
    public List<ProductSummaryResponse> listVariations(@PathVariable UUID id) {
        return productService.listVariations(id);
    }

    @GetMapping
    public Page<ProductSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minSellingPrice,
            @RequestParam(required = false) BigDecimal maxSellingPrice,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return productService.list(pageable, q, sku, category, mainCategory, subCategory, vendorId, type, minSellingPrice, maxSellingPrice);
    }
}
