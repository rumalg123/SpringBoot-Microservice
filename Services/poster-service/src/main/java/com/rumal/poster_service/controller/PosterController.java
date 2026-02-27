package com.rumal.poster_service.controller;

import com.rumal.poster_service.config.PosterClickRateLimiter;
import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.SlugAvailabilityResponse;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.service.PosterService;
import com.rumal.poster_service.storage.PosterImageStorageService;
import com.rumal.poster_service.storage.StoredImage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/posters")
@RequiredArgsConstructor
public class PosterController {

    private final PosterService posterService;
    private final PosterImageStorageService posterImageStorageService;
    private final PosterClickRateLimiter rateLimiter;

    @GetMapping
    public Page<PosterResponse> list(
            @RequestParam(required = false) PosterPlacement placement,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return placement == null
                ? posterService.listAllActive(pageable)
                : posterService.listActiveByPlacement(placement, pageable);
    }

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse isSlugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        return new SlugAvailabilityResponse(slug, posterService.isSlugAvailable(slug, excludeId));
    }

    @GetMapping("/images/**")
    public ResponseEntity<byte[]> getImage(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = new AntPathMatcher().extractPathWithinPattern(bestPattern, path);
        StoredImage image = posterImageStorageService.getImage(key);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }

    @GetMapping("/{idOrSlug}")
    public PosterResponse getByIdOrSlug(@PathVariable String idOrSlug) {
        return posterService.getByIdOrSlug(idOrSlug);
    }

    @PostMapping("/{id}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordClick(@PathVariable UUID id, HttpServletRequest request) {
        if (!rateLimiter.isAllowed(request, id)) return;
        posterService.recordClick(id);
    }

    @PostMapping("/{id}/impression")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordImpression(@PathVariable UUID id, HttpServletRequest request) {
        if (!rateLimiter.isAllowed(request, id)) return;
        posterService.recordImpression(id);
    }

    @PostMapping("/{posterId}/variants/{variantId}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordVariantClick(
            @PathVariable UUID posterId,
            @PathVariable UUID variantId,
            HttpServletRequest request
    ) {
        if (!rateLimiter.isAllowed(request, posterId)) return;
        posterService.recordVariantClick(posterId, variantId);
    }

    @PostMapping("/{posterId}/variants/{variantId}/impression")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordVariantImpression(
            @PathVariable UUID posterId,
            @PathVariable UUID variantId,
            HttpServletRequest request
    ) {
        if (!rateLimiter.isAllowed(request, posterId)) return;
        posterService.recordVariantImpression(posterId, variantId);
    }
}
