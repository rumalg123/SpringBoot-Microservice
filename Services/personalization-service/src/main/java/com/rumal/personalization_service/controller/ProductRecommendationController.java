package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.service.BoughtTogetherService;
import com.rumal.personalization_service.service.SimilarProductsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personalization/products")
@RequiredArgsConstructor
public class ProductRecommendationController {

    private final SimilarProductsService similarProductsService;
    private final BoughtTogetherService boughtTogetherService;

    @GetMapping("/{productId}/similar")
    public List<ProductSummary> getSimilar(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return similarProductsService.getSimilarProducts(productId, limit);
    }

    @GetMapping("/{productId}/bought-together")
    public List<ProductSummary> getBoughtTogether(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return boughtTogetherService.getBoughtTogether(productId, limit);
    }
}
