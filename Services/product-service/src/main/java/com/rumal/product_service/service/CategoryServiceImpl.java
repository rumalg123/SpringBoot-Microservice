package com.rumal.product_service.service;

import com.rumal.product_service.dto.CategoryAttributeResponse;
import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.UpsertCategoryAttributeRequest;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.entity.AttributeType;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.CategoryAttribute;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.CategoryAttributeRepository;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final ProductRepository productRepository;
    private final ProductCatalogReadModelProjector productCatalogReadModelProjector;
    private final ProductCacheVersionService productCacheVersionService;

    @Value("${category.max-depth:4}")
    private int maxCategoryDepth;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true)
    })
    public CategoryResponse create(UpsertCategoryRequest request) {
        String normalizedName = normalizeName(request.name());
        if (categoryRepository.existsByNormalizedName(normalizedName)) {
            throw new ValidationException("Category name must be unique: " + request.name());
        }
        String requestedSlug = normalizeRequestedSlug(request.slug());
        boolean autoSlug = requestedSlug.isEmpty();
        String baseSlug = autoSlug ? SlugUtils.toSlug(request.name()) : requestedSlug;
        if (!autoSlug && categoryRepository.existsBySlug(baseSlug)) {
            throw new ValidationException("Category slug must be unique: " + baseSlug);
        }

        Category category = Category.builder().build();
        applyRequest(category, request, baseSlug, autoSlug);
        Category saved = categoryRepository.save(category);
        saveCategoryAttributes(saved, request.attributes());
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true)
    })
    public CategoryResponse update(UUID id, UpsertCategoryRequest request) {
        Category category = getActiveById(id);
        String normalizedName = normalizeName(request.name());
        if (categoryRepository.existsByNormalizedNameAndIdNot(normalizedName, id)) {
            throw new ValidationException("Category name must be unique: " + request.name());
        }
        String requestedSlug = normalizeRequestedSlug(request.slug());
        boolean autoSlug = requestedSlug.isEmpty();
        String baseSlug = autoSlug ? SlugUtils.toSlug(request.name()) : requestedSlug;
        if (!autoSlug && categoryRepository.existsBySlugAndIdNot(baseSlug, id)) {
            throw new ValidationException("Category slug must be unique: " + baseSlug);
        }

        applyRequest(category, request, baseSlug, autoSlug);
        Category saved = categoryRepository.save(category);
        saveCategoryAttributes(saved, request.attributes());
        productCatalogReadModelProjector.rebuildAll();
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true)
    })
    public void softDelete(UUID id) {
        Category category = getActiveById(id);

        if (category.getType() == CategoryType.PARENT
                && categoryRepository.existsByDeletedFalseAndParentCategoryId(id)) {
            throw new ValidationException("Cannot delete parent category with active sub categories");
        }

        if (productRepository.existsByCategories_IdAndDeletedFalseAndActiveTrue(id)) {
            throw new ValidationException("Cannot delete category assigned to active products");
        }

        category.setDeleted(true);
        category.setDeletedAt(Instant.now());
        categoryRepository.save(category);
        productCatalogReadModelProjector.rebuildAll();
        productCacheVersionService.bumpAllProductReadCaches();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true)
    })
    public CategoryResponse restore(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (!category.isDeleted()) {
            throw new ValidationException("Category is not soft deleted: " + id);
        }

        if (category.getParentCategoryId() != null) {
            getActiveById(category.getParentCategoryId());
        }

        category.setDeleted(false);
        category.setDeletedAt(null);
        Category saved = categoryRepository.save(category);
        productCatalogReadModelProjector.rebuildAll();
        productCacheVersionService.bumpAllProductReadCaches();
        return toResponse(saved);
    }

    @Override
    @Cacheable(
            cacheNames = "categoriesList",
            key = "'type=' + #type + '::parent=' + #parentCategoryId"
    )
    public List<CategoryResponse> listActive(CategoryType type, UUID parentCategoryId) {
        List<Category> categories;
        if (parentCategoryId != null) {
            categories = categoryRepository.findByDeletedFalseAndParentCategoryIdOrderByNameAsc(parentCategoryId);
        } else if (type != null) {
            categories = categoryRepository.findByDeletedFalseAndTypeOrderByNameAsc(type);
        } else {
            categories = categoryRepository.findByDeletedFalseOrderByNameAsc();
        }
        Map<UUID, List<CategoryAttribute>> attrs = batchFetchAttributes(categories);
        return categories.stream().map(c -> toResponse(c, attrs.getOrDefault(c.getId(), List.of()))).toList();
    }

    @Override
    public Page<CategoryResponse> listActivePaged(CategoryType type, UUID parentCategoryId, Pageable pageable) {
        Page<Category> categories;
        if (parentCategoryId != null) {
            categories = categoryRepository.findByDeletedFalseAndParentCategoryIdOrderByNameAsc(parentCategoryId, pageable);
        } else if (type != null) {
            categories = categoryRepository.findByDeletedFalseAndTypeOrderByNameAsc(type, pageable);
        } else {
            categories = categoryRepository.findByDeletedFalseOrderByNameAsc(pageable);
        }
        Map<UUID, List<CategoryAttribute>> attrs = batchFetchAttributes(categories.getContent());
        return categories.map(c -> toResponse(c, attrs.getOrDefault(c.getId(), List.of())));
    }

    @Override
    public Page<CategoryResponse> listDeletedPaged(Pageable pageable) {
        Page<Category> page = categoryRepository.findByDeletedTrueOrderByUpdatedAtDesc(pageable);
        Map<UUID, List<CategoryAttribute>> attrs = batchFetchAttributes(page.getContent());
        return page.map(c -> toResponse(c, attrs.getOrDefault(c.getId(), List.of())));
    }

    @Override
    @Cacheable(cacheNames = "deletedCategoriesList", key = "'deleted'")
    public List<CategoryResponse> listDeleted() {
        List<Category> categories = categoryRepository.findByDeletedTrueOrderByUpdatedAtDesc();
        Map<UUID, List<CategoryAttribute>> attrs = batchFetchAttributes(categories);
        return categories.stream()
                .map(c -> toResponse(c, attrs.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Override
    public boolean isSlugAvailable(String slug, UUID excludeId) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (normalizedSlug.isEmpty()) {
            return false;
        }
        if (excludeId == null) {
            return !categoryRepository.existsBySlug(normalizedSlug);
        }
        return !categoryRepository.existsBySlugAndIdNot(normalizedSlug, excludeId);
    }

    private void applyRequest(Category category, UpsertCategoryRequest request, String baseSlug, boolean autoSlug) {
        String normalizedName = normalizeName(request.name());
        UUID parentId = request.parentCategoryId();
        String resolvedSlug = resolveUniqueSlug(baseSlug, category.getId(), autoSlug);

        // Gap 5: PARENT -> SUB type change guard
        if (category.getId() != null && category.getType() == CategoryType.PARENT && request.type() == CategoryType.SUB) {
            if (categoryRepository.existsByDeletedFalseAndParentCategoryId(category.getId())) {
                throw new ValidationException("Cannot change category type from PARENT to SUB while it has active child categories");
            }
        }

        int depth = 0;
        String path = "/";

        if (request.type() == CategoryType.PARENT && parentId == null) {
            // Root-level parent category: depth 0
            depth = 0;
            path = "/";
        } else if (parentId == null) {
            throw new ValidationException("Sub category must have parentCategoryId");
        } else {
            Category parent = getActiveById(parentId);
            if (parent.getId().equals(category.getId())) {
                throw new ValidationException("Category cannot be parent of itself");
            }
            depth = parent.getDepth() + 1;
            if (depth >= maxCategoryDepth) {
                throw new ValidationException("Category nesting depth cannot exceed " + maxCategoryDepth + " levels");
            }
            String parentPath = parent.getPath() != null ? parent.getPath() : "/";
            path = parentPath.endsWith("/")
                    ? parentPath + parent.getSlug() + "/"
                    : parentPath + "/" + parent.getSlug() + "/";
        }

        category.setName(request.name().trim());
        category.setSlug(resolvedSlug);
        category.setNormalizedName(normalizedName);
        category.setType(request.type());
        category.setParentCategoryId(parentId);
        category.setDepth(depth);
        category.setPath(path);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        category.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        category.setDeleted(false);
        category.setDeletedAt(null);
    }

    private void saveCategoryAttributes(Category category, List<UpsertCategoryAttributeRequest> attributes) {
        categoryAttributeRepository.deleteByCategoryId(category.getId());
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        Set<String> keys = new HashSet<>();
        List<CategoryAttribute> entities = new ArrayList<>();
        for (int i = 0; i < attributes.size(); i++) {
            UpsertCategoryAttributeRequest attr = attributes.get(i);
            String normalizedKey = attr.attributeKey().trim().toLowerCase(Locale.ROOT);
            if (normalizedKey.isEmpty()) {
                throw new ValidationException("attributeKey is required");
            }
            if (!keys.add(normalizedKey)) {
                throw new ValidationException("duplicate category attribute key: " + normalizedKey);
            }
            if (attr.attributeType() == AttributeType.SELECT) {
                if (attr.allowedValues() == null || attr.allowedValues().isEmpty()) {
                    throw new ValidationException("allowedValues is required for SELECT attribute type: " + normalizedKey);
                }
            }
            String allowedValuesCsv = attr.allowedValues() != null && !attr.allowedValues().isEmpty()
                    ? String.join(",", attr.allowedValues())
                    : null;
            entities.add(CategoryAttribute.builder()
                    .category(category)
                    .attributeKey(normalizedKey)
                    .attributeLabel(attr.attributeLabel().trim())
                    .required(attr.required())
                    .displayOrder(attr.displayOrder() != null ? attr.displayOrder() : i)
                    .attributeType(attr.attributeType())
                    .allowedValues(allowedValuesCsv)
                    .build());
        }
        categoryAttributeRepository.saveAll(entities);
    }

    private Category getActiveById(UUID id) {
        return categoryRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequestedSlug(String slug) {
        String normalized = SlugUtils.toSlug(slug);
        return normalized.length() > 130 ? normalized.substring(0, 130) : normalized;
    }

    private String resolveUniqueSlug(String baseSlug, UUID existingId, boolean allowAutoSuffix) {
        String seed = baseSlug == null || baseSlug.isBlank() ? "category" : baseSlug;
        String normalizedSeed = seed.length() > 130 ? seed.substring(0, 130) : seed;
        if (isSlugAvailable(normalizedSeed, existingId)) {
            return normalizedSeed;
        }
        if (!allowAutoSuffix) {
            throw new ValidationException("Category slug must be unique: " + normalizedSeed);
        }
        int suffix = 2;
        while (suffix < 100_000) {
            String candidate = appendSlugSuffix(normalizedSeed, suffix, 130);
            if (isSlugAvailable(candidate, existingId)) {
                return candidate;
            }
            suffix++;
        }
        throw new ValidationException("Unable to generate a unique category slug");
    }

    private String appendSlugSuffix(String slug, int suffix, int maxLen) {
        String suffixPart = "-" + suffix;
        int allowedBaseLength = Math.max(1, maxLen - suffixPart.length());
        String base = slug.length() > allowedBaseLength ? slug.substring(0, allowedBaseLength) : slug;
        return base + suffixPart;
    }

    private CategoryResponse toResponse(Category category) {
        return toResponse(category, categoryAttributeRepository
                .findByCategoryIdOrderByDisplayOrderAsc(category.getId()));
    }

    private CategoryResponse toResponse(Category category, List<CategoryAttribute> attributes) {
        List<CategoryAttributeResponse> attributeResponses = attributes.stream()
                .map(this::toCategoryAttributeResponse)
                .toList();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getType(),
                category.getParentCategoryId(),
                category.getDescription(),
                category.getImageUrl(),
                category.getDepth(),
                category.getPath(),
                category.getDisplayOrder(),
                attributeResponses,
                category.isDeleted(),
                category.getDeletedAt(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private Map<UUID, List<CategoryAttribute>> batchFetchAttributes(List<Category> categories) {
        if (categories.isEmpty()) return Map.of();
        Set<UUID> ids = categories.stream().map(Category::getId).collect(Collectors.toSet());
        return categoryAttributeRepository.findByCategoryIdInOrderByDisplayOrderAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getCategory().getId()));
    }

    private CategoryAttributeResponse toCategoryAttributeResponse(CategoryAttribute attr) {
        List<String> allowedValues = attr.getAllowedValues() != null && !attr.getAllowedValues().isBlank()
                ? Arrays.asList(attr.getAllowedValues().split(","))
                : List.of();
        return new CategoryAttributeResponse(
                attr.getId(),
                attr.getAttributeKey(),
                attr.getAttributeLabel(),
                attr.isRequired(),
                attr.getDisplayOrder(),
                attr.getAttributeType(),
                allowedValues
        );
    }
}
