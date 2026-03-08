package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.PublicVendorResponse;
import com.rumal.vendor_service.dto.SlugAvailabilityResponse;
import com.rumal.vendor_service.service.StoredImage;
import com.rumal.vendor_service.service.VendorService;
import com.rumal.vendor_service.service.VendorMediaStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final VendorMediaStorageService vendorMediaStorageService;

    @GetMapping
    public Page<PublicVendorResponse> listActive(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return vendorService.listPublicActive(category, pageable);
    }

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse slugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        return new SlugAvailabilityResponse(vendorService.isSlugAvailable(slug, excludeId));
    }

    @GetMapping("/media/**")
    public ResponseEntity<byte[]> getMedia(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = new AntPathMatcher().extractPathWithinPattern(bestPattern, path);
        StoredImage image = vendorMediaStorageService.getImage(key);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }

    @GetMapping("/{idOrSlug}")
    public PublicVendorResponse getByIdOrSlug(@PathVariable String idOrSlug) {
        return vendorService.getPublicByIdOrSlug(idOrSlug);
    }
}
