package com.rumal.product_service.repo;

import com.rumal.product_service.entity.Category;
import com.rumal.product_service.entity.CategoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByNormalizedName(String normalizedName);
    Optional<Category> findBySlug(String slug);
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Category> findByDeletedFalseOrderByNameAsc();
    List<Category> findByDeletedTrueOrderByUpdatedAtDesc();
    List<Category> findByDeletedFalseAndTypeOrderByNameAsc(CategoryType type);
    List<Category> findByDeletedFalseAndParentCategoryIdOrderByNameAsc(UUID parentCategoryId);
    boolean existsByDeletedFalseAndParentCategoryId(UUID parentCategoryId);

    // Paginated queries
    Page<Category> findByDeletedFalseOrderByNameAsc(Pageable pageable);
    Page<Category> findByDeletedFalseAndTypeOrderByNameAsc(CategoryType type, Pageable pageable);
    Page<Category> findByDeletedFalseAndParentCategoryIdOrderByNameAsc(UUID parentCategoryId, Pageable pageable);
    Page<Category> findByDeletedTrueOrderByUpdatedAtDesc(Pageable pageable);

    // Count children for type-change guard
    long countByDeletedFalseAndParentCategoryId(UUID parentCategoryId);
}
