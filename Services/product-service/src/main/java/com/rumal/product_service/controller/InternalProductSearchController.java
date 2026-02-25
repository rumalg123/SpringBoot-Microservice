package com.rumal.product_service.controller;

import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/internal/products/search")
@RequiredArgsConstructor
public class InternalProductSearchController {

    private final ProductService productService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/catalog")
    public Page<ProductSummaryResponse> catalogPage(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        internalRequestVerifier.verify(internalAuth);
        return productService.list(
                PageRequest.of(page, Math.min(size, 200), Sort.by("updatedAt").ascending()),
                null, null, null, null, null, null, null,
                null, null, false, null, null, null, null, null, null
        );
    }

    @GetMapping("/updated-since")
    public Page<ProductSummaryResponse> updatedSince(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @RequestParam Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        internalRequestVerifier.verify(internalAuth);
        return productService.listUpdatedSince(
                since,
                PageRequest.of(page, Math.min(size, 200), Sort.by("updatedAt").ascending())
        );
    }
}
