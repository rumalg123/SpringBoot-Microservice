package com.rumal.product_service.repo;

import com.rumal.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    List<Product> findByParentProductIdAndDeletedFalseAndActiveTrue(UUID parentProductId);
    boolean existsByCategories_IdAndDeletedFalseAndActiveTrue(UUID categoryId);
}
