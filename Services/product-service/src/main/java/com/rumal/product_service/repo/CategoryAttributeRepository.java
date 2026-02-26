package com.rumal.product_service.repo;

import com.rumal.product_service.entity.CategoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, UUID> {
    List<CategoryAttribute> findByCategoryIdOrderByDisplayOrderAsc(UUID categoryId);
    List<CategoryAttribute> findByCategoryIdInOrderByDisplayOrderAsc(Collection<UUID> categoryIds);
    void deleteByCategoryId(UUID categoryId);
}
