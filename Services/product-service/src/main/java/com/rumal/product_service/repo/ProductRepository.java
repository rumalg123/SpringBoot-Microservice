package com.rumal.product_service.repo;

import com.rumal.product_service.entity.Product;
import com.rumal.product_service.entity.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    List<Product> findByParentProductIdAndDeletedFalseAndActiveTrue(UUID parentProductId);
    boolean existsByParentProductIdAndDeletedFalseAndActiveTrueAndProductType(UUID parentProductId, ProductType productType);
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsByCategories_IdAndDeletedFalseAndActiveTrue(UUID categoryId);

    @Query("""
            select distinct p.parentProductId
            from Product p
            where p.parentProductId is not null
              and p.deleted = false
              and p.active = true
              and p.productType = com.rumal.product_service.entity.ProductType.VARIATION
            """)
    Set<UUID> findParentIdsWithActiveVariationChildren();
}
