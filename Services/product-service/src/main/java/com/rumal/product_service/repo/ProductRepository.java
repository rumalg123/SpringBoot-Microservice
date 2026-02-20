package com.rumal.product_service.repo;

import com.rumal.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    List<Product> findByParentProductIdAndDeletedFalseAndActiveTrue(UUID parentProductId);
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsByCategories_IdAndDeletedFalseAndActiveTrue(UUID categoryId);
}
