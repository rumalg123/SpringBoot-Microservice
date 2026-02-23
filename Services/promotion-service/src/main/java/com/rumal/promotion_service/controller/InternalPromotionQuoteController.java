package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.PromotionQuoteRequest;
import com.rumal.promotion_service.dto.PromotionQuoteResponse;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.PromotionQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/promotions")
@RequiredArgsConstructor
public class InternalPromotionQuoteController {

    private final PromotionQuoteService promotionQuoteService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/quote")
    public PromotionQuoteResponse quote(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody PromotionQuoteRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return promotionQuoteService.quote(request);
    }
}
