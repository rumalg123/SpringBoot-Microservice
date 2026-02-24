package com.rumal.product_service.controller;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.SlugAvailabilityResponse;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/slug-available")
    public SlugAvailabilityResponse isSlugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        boolean available = categoryService.isSlugAvailable(slug, excludeId);
        return new SlugAvailabilityResponse(slug, available);
    }

    @GetMapping
    public List<CategoryResponse> list(
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) UUID parentCategoryId
    ) {
        return categoryService.listActive(type, parentCategoryId);
    }

    @GetMapping("/paged")
    public Page<CategoryResponse> listPaged(
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) UUID parentCategoryId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return categoryService.listActivePaged(type, parentCategoryId, pageable);
    }
}
