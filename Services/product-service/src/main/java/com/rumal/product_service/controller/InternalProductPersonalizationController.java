package com.rumal.product_service.controller;

import com.rumal.product_service.dto.BatchProductRequest;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/products/personalization")
@RequiredArgsConstructor
public class InternalProductPersonalizationController {

    private final ProductService productService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/batch-summaries")
    public List<ProductSummaryResponse> batchSummaries(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody BatchProductRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return productService.getByIds(request.productIds());
    }
}
