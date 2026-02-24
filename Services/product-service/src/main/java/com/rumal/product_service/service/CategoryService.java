package com.rumal.product_service.service;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.entity.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    CategoryResponse create(UpsertCategoryRequest request);
    CategoryResponse update(UUID id, UpsertCategoryRequest request);
    void softDelete(UUID id);
    CategoryResponse restore(UUID id);
    boolean isSlugAvailable(String slug, UUID excludeId);
    List<CategoryResponse> listActive(CategoryType type, UUID parentCategoryId);
    Page<CategoryResponse> listActivePaged(CategoryType type, UUID parentCategoryId, Pageable pageable);
    List<CategoryResponse> listDeleted();
    Page<CategoryResponse> listDeletedPaged(Pageable pageable);
}
