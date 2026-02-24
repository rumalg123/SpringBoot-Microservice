package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.PublicPromotionResponse;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.service.PublicPromotionService;
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

import java.util.UUID;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
public class PublicPromotionController {

    private final PublicPromotionService publicPromotionService;

    @GetMapping
    public Page<PublicPromotionResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PromotionScopeType scopeType,
            @RequestParam(required = false) PromotionBenefitType benefitType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return publicPromotionService.list(pageable, q, scopeType, benefitType);
    }

    @GetMapping("/flash-sales")
    public Page<PublicPromotionResponse> listFlashSales(
            @PageableDefault(size = 20, sort = "flashSaleStartAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return publicPromotionService.listActiveFlashSales(pageable);
    }

    @GetMapping("/{id}")
    public PublicPromotionResponse get(@PathVariable UUID id) {
        return publicPromotionService.get(id);
    }
}
