package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductCatalogRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ProductCatalogReadRepository extends JpaRepository<ProductCatalogRead, UUID>, JpaSpecificationExecutor<ProductCatalogRead> {
}
