package com.rumal.product_service.service;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.entity.CategoryType;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    CategoryResponse create(UpsertCategoryRequest request);
    CategoryResponse update(UUID id, UpsertCategoryRequest request);
    void softDelete(UUID id);
    CategoryResponse restore(UUID id);
    boolean isSlugAvailable(String slug, UUID excludeId);
    List<CategoryResponse> listActive(CategoryType type, UUID parentCategoryId);
    List<CategoryResponse> listDeleted();
}
