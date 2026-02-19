package com.rumal.product_service.controller;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public List<CategoryResponse> list(
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) UUID parentCategoryId
    ) {
        return categoryService.listActive(type, parentCategoryId);
    }
}

