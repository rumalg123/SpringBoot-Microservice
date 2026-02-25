package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.service.RecentlyViewedService;
import com.rumal.personalization_service.service.RecommendationService;
import com.rumal.personalization_service.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personalization")
@RequiredArgsConstructor
public class PersonalizationController {

    private final RecommendationService recommendationService;
    private final RecentlyViewedService recentlyViewedService;
    private final TrendingService trendingService;

    @GetMapping("/me/recommended")
    public List<ProductSummary> getRecommended(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId != null) {
            return recommendationService.getRecommendationsForUser(userId, limit);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return recommendationService.getRecommendationsForAnonymous(sessionId, limit);
        }
        return trendingService.getTrending(limit);
    }

    @GetMapping("/me/recently-viewed")
    public List<ProductSummary> getRecentlyViewed(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            return List.of();
        }
        return recentlyViewedService.get(userId, sessionId, limit);
    }

    @GetMapping("/trending")
    public List<ProductSummary> getTrending(@RequestParam(defaultValue = "20") int limit) {
        return trendingService.getTrending(limit);
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
