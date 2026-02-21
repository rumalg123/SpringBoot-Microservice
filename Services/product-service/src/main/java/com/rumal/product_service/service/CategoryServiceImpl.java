package com.rumal.product_service.service;

import com.rumal.product_service.dto.CategoryResponse;
import com.rumal.product_service.dto.UpsertCategoryRequest;
import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.CategoryType;
import com.rumal.product_service.exception.ResourceNotFoundException;
import com.rumal.product_service.exception.ValidationException;
import com.rumal.product_service.repo.CategoryRepository;
import com.rumal.product_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true),
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
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
        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true),
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
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
        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true),
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
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
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "categoriesList", allEntries = true),
            @CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true),
            @CacheEvict(cacheNames = "productsList", allEntries = true),
            @CacheEvict(cacheNames = "deletedProductsList", allEntries = true),
            @CacheEvict(cacheNames = "productById", allEntries = true)
    })
    public CategoryResponse restore(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (!category.isDeleted()) {
            throw new ValidationException("Category is not soft deleted: " + id);
        }

        if (category.getType() == CategoryType.SUB && category.getParentCategoryId() != null) {
            Category parent = getActiveById(category.getParentCategoryId());
            if (parent.getType() != CategoryType.PARENT) {
                throw new ValidationException("Sub category parent must be a parent category");
            }
        }

        category.setDeleted(false);
        category.setDeletedAt(null);
        return toResponse(categoryRepository.save(category));
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
        return categories.stream().map(this::toResponse).toList();
    }

    @Override
    @Cacheable(cacheNames = "deletedCategoriesList", key = "'deleted'")
    public List<CategoryResponse> listDeleted() {
        return categoryRepository.findByDeletedTrueOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
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

        if (request.type() == CategoryType.PARENT) {
            if (parentId != null) {
                throw new ValidationException("Parent category cannot have parentCategoryId");
            }
        } else {
            if (parentId == null) {
                throw new ValidationException("Sub category must have parentCategoryId");
            }
            Category parent = getActiveById(parentId);
            if (parent.getType() != CategoryType.PARENT) {
                throw new ValidationException("Sub category parent must be a parent category");
            }
            if (parent.getId().equals(category.getId())) {
                throw new ValidationException("Category cannot be parent of itself");
            }
        }

        category.setName(request.name().trim());
        category.setSlug(resolvedSlug);
        category.setNormalizedName(normalizedName);
        category.setType(request.type());
        category.setParentCategoryId(parentId);
        category.setDeleted(false);
        category.setDeletedAt(null);
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
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getType(),
                category.getParentCategoryId(),
                category.isDeleted(),
                category.getDeletedAt(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
