package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, UUID> {

    List<CatalogProduct> findByProductIdIn(Collection<UUID> productIds);
}
