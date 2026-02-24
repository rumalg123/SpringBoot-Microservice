package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, UUID> {
    List<ProductSpecification> findByProductIdOrderByDisplayOrderAsc(UUID productId);
    void deleteByProductId(UUID productId);
}
