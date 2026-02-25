package com.rumal.wishlist_service.controller;

import com.rumal.wishlist_service.dto.analytics.*;
import com.rumal.wishlist_service.security.InternalRequestVerifier;
import com.rumal.wishlist_service.service.WishlistAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/wishlist/analytics")
@RequiredArgsConstructor
public class InternalWishlistAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final WishlistAnalyticsService wishlistAnalyticsService;

    @GetMapping("/platform/summary")
    public WishlistPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return wishlistAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/most-wished")
    public List<MostWishedProduct> mostWished(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "20") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return wishlistAnalyticsService.getMostWished(limit);
    }
}
