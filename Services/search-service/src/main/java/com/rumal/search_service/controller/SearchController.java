package com.rumal.search_service.controller;

import com.rumal.search_service.dto.*;
import com.rumal.search_service.service.AutocompleteService;
import com.rumal.search_service.service.PopularSearchService;
import com.rumal.search_service.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchService productSearchService;
    private final AutocompleteService autocompleteService;
    private final PopularSearchService popularSearchService;

    private static final int MAX_QUERY_LENGTH = 256;

    @GetMapping("/products")
    public SearchResponse searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String mainCategory,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        if (size > 50) size = 50;
        if (size < 1) size = 1;
        if (page < 0) page = 0;
        int maxPage = (10_000 / size) - 1;
        if (page > maxPage) page = maxPage;

        var request = new SearchRequest(q, category, mainCategory, subCategory,
                brand, minPrice, maxPrice, vendorId, sortBy, page, size);
        return productSearchService.search(request);
    }

    @GetMapping("/autocomplete")
    public AutocompleteResponse autocomplete(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "8") int limit
    ) {
        if (prefix == null || prefix.trim().length() < 1) {
            return new AutocompleteResponse(List.of(), popularSearchService.getPopularSearches());
        }
        return autocompleteService.autocomplete(prefix.trim(), Math.min(limit, 15));
    }

    @GetMapping("/popular")
    public PopularSearchResponse popularSearches() {
        return new PopularSearchResponse(popularSearchService.getPopularSearches());
    }
}
