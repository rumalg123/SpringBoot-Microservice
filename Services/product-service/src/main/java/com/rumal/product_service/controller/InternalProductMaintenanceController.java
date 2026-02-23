package com.rumal.product_service.controller;

import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductMaintenanceController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final ProductService productService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/cache/vendors/{vendorId}/evict")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evictVendorCaches(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        productService.evictPublicCachesForVendorVisibilityChange(vendorId);
    }

    @PostMapping("/vendors/{vendorId}/deactivate-all")
    public java.util.Map<String, Object> deactivateAllByVendor(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        int count = productService.deactivateAllByVendor(vendorId);
        return java.util.Map.of("vendorId", vendorId, "deactivatedCount", count);
    }
}
