package com.rumal.product_service.controller;

import com.rumal.product_service.dto.ProductResponse;
import com.rumal.product_service.dto.ProductSummaryResponse;
import com.rumal.product_service.dto.SlugAvailabilityResponse;
import com.rumal.product_service.entity.ProductType;
import com.rumal.product_service.service.ProductService;
import com.rumal.product_service.storage.ProductImageStorageService;
import com.rumal.product_service.storage.StoredImage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImageStorageService productImageStorageService;

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse isSlugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        boolean available = productService.isSlugAvailable(slug, excludeId);
        return new SlugAvailabilityResponse(slug, available);
    }

    @GetMapping("/{idOrSlug}")
    public ProductResponse getByIdOrSlug(@PathVariable String idOrSlug) {
        return productService.getByIdOrSlug(idOrSlug);
    }

    @GetMapping("/{idOrSlug}/variations")
    public List<ProductSummaryResponse> listVariations(@PathVariable String idOrSlug) {
        return productService.listVariationsByIdOrSlug(idOrSlug);
    }

    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordView(@PathVariable UUID id) {
        productService.incrementViewCount(id);
    }

    @GetMapping("/images/**")
    public ResponseEntity<byte[]> getImage(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = new AntPathMatcher().extractPathWithinPattern(bestPattern, path);
        StoredImage image = productImageStorageService.getImage(key);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }

    @GetMapping
    public Page<ProductSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) BigDecimal minSellingPrice,
            @RequestParam(required = false) BigDecimal maxSellingPrice,
            @RequestParam(defaultValue = "false") boolean includeOrphanParents,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String specs,
            @RequestParam(required = false) String vendorName,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) String sortBy,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Pageable effectivePageable = applySortByPreset(sortBy, pageable);
        Map<String, String> specFilter = parseSpecsParam(specs);
        return productService.list(
                effectivePageable,
                q,
                sku,
                category,
                mainCategory,
                subCategory,
                vendorId,
                type,
                minSellingPrice,
                maxSellingPrice,
                includeOrphanParents,
                brand,
                null,
                specFilter,
                vendorName,
                createdAfter,
                createdBefore
        );
    }

    private Pageable applySortByPreset(String sortBy, Pageable pageable) {
        if (sortBy == null || sortBy.isBlank()) {
            return pageable;
        }
        Sort sort = switch (sortBy.trim().toLowerCase()) {
            case "popularity" -> Sort.by(Sort.Direction.DESC, "soldCount")
                    .and(Sort.by(Sort.Direction.DESC, "viewCount"));
            case "best-selling" -> Sort.by(Sort.Direction.DESC, "soldCount");
            case "most-viewed" -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "price-low" -> Sort.by(Sort.Direction.ASC, "sellingPrice");
            case "price-high" -> Sort.by(Sort.Direction.DESC, "sellingPrice");
            default -> null;
        };
        if (sort == null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Map<String, String> parseSpecsParam(String specs) {
        if (specs == null || specs.isBlank()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = specs.split(",");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex <= 0 || colonIndex >= pair.length() - 1) {
                continue;
            }
            String key = pair.substring(0, colonIndex).trim();
            String value = pair.substring(colonIndex + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                result.put(key.toLowerCase(), value);
            }
        }
        return result.isEmpty() ? null : result;
    }
}
