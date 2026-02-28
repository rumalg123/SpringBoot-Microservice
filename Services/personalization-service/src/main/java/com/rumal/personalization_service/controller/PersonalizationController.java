package com.rumal.personalization_service.controller;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.service.RecentlyViewedService;
import com.rumal.personalization_service.service.RecommendationService;
import com.rumal.personalization_service.service.TrackingOptOutService;
import com.rumal.personalization_service.service.TrendingService;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/personalization")
@RequiredArgsConstructor
public class PersonalizationController {

    private final RecommendationService recommendationService;
    private final RecentlyViewedService recentlyViewedService;
    private final TrendingService trendingService;
    private final TrackingOptOutService trackingOptOutService;

    @GetMapping("/me/recommended")
    public List<ProductSummary> getRecommended(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestParam(defaultValue = "20") @Max(100) int limit
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
            @RequestParam(defaultValue = "20") @Max(100) int limit
    ) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            return List.of();
        }
        return recentlyViewedService.get(userId, sessionId, limit);
    }

    @GetMapping("/trending")
    public List<ProductSummary> getTrending(@RequestParam(defaultValue = "20") @Max(100) int limit) {
        return trendingService.getTrending(limit);
    }

    @PostMapping("/me/tracking/opt-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void optOutOfTracking(@RequestHeader(value = "X-User-Sub") String userSub) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid user identity required");
        }
        trackingOptOutService.optOut(userId);
    }

    @PostMapping("/me/tracking/opt-in")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void optInToTracking(@RequestHeader(value = "X-User-Sub") String userSub) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid user identity required");
        }
        trackingOptOutService.optIn(userId);
    }

    @GetMapping("/me/tracking/status")
    public java.util.Map<String, Boolean> getTrackingStatus(@RequestHeader(value = "X-User-Sub") String userSub) {
        UUID userId = parseUuidOrNull(userSub);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid user identity required");
        }
        boolean optedOut = trackingOptOutService.hasOptedOut(userId);
        return java.util.Map.of("trackingEnabled", !optedOut);
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
