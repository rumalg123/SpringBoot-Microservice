package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductCatalogRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rumal.product_service.entity.ApprovalStatus;

import java.util.UUID;

public interface ProductCatalogReadRepository extends JpaRepository<ProductCatalogRead, UUID>, JpaSpecificationExecutor<ProductCatalogRead> {

    @Modifying
    @Query("update ProductCatalogRead r set r.viewCount = r.viewCount + 1 where r.id = :productId")
    int incrementViewCount(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductCatalogRead r set r.approvalStatus = :to where r.approvalStatus = :from")
    int bulkUpdateApprovalStatus(@Param("from") ApprovalStatus from, @Param("to") ApprovalStatus to);
}
