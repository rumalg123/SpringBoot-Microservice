package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.Warehouse;
import com.rumal.inventory_service.entity.WarehouseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Page<Warehouse> findByActive(boolean active, Pageable pageable);

    Page<Warehouse> findByWarehouseType(WarehouseType warehouseType, Pageable pageable);

    Page<Warehouse> findByVendorId(UUID vendorId, Pageable pageable);

    Page<Warehouse> findByVendorIdAndActive(UUID vendorId, boolean active, Pageable pageable);

    @Query("""
            select w from Warehouse w
            where (:vendorId is null or w.vendorId = :vendorId)
              and (:warehouseType is null or w.warehouseType = :warehouseType)
              and (:active is null or w.active = :active)
            """)
    Page<Warehouse> findFiltered(
            @Param("vendorId") UUID vendorId,
            @Param("warehouseType") WarehouseType warehouseType,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
