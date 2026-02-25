package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.MovementType;
import com.rumal.inventory_service.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByStockItemId(UUID stockItemId, Pageable pageable);

    Page<StockMovement> findByProductId(UUID productId, Pageable pageable);

    Page<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId, Pageable pageable);

    @Query("""
            select m from StockMovement m
            where (:productId is null or m.productId = :productId)
              and (:warehouseId is null or m.warehouseId = :warehouseId)
              and (:movementType is null or m.movementType = :movementType)
            order by m.createdAt desc
            """)
    Page<StockMovement> findFiltered(
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId,
            @Param("movementType") MovementType movementType,
            Pageable pageable
    );

    @Query("""
            select m from StockMovement m
            where m.stockItem.vendorId = :vendorId
            order by m.createdAt desc
            """)
    Page<StockMovement> findByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);
}
