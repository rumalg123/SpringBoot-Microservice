package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.StockItem;
import com.rumal.inventory_service.entity.StockStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    List<StockItem> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s join fetch s.warehouse w where s.productId = :productId and w.active = true order by s.quantityAvailable desc")
    List<StockItem> findByProductIdForUpdateOrderByAvailableDesc(@Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.id = :id")
    Optional<StockItem> findByIdForUpdate(@Param("id") UUID id);

    @Query("select s from StockItem s join fetch s.warehouse w where s.productId in :productIds and w.active = true")
    List<StockItem> findByProductIdIn(@Param("productIds") List<UUID> productIds);

    Page<StockItem> findByVendorId(UUID vendorId, Pageable pageable);

    @Query("""
            select s from StockItem s join fetch s.warehouse w
            where s.vendorId = :vendorId
              and s.quantityAvailable <= s.lowStockThreshold
            """)
    Page<StockItem> findLowStockByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);

    @Query("select s from StockItem s join fetch s.warehouse w where s.quantityAvailable <= s.lowStockThreshold")
    Page<StockItem> findLowStock(Pageable pageable);

    @Query("""
            select s from StockItem s join fetch s.warehouse w
            where (:vendorId is null or s.vendorId = :vendorId)
              and (:productId is null or s.productId = :productId)
              and (:warehouseId is null or w.id = :warehouseId)
              and (:stockStatus is null or s.stockStatus = :stockStatus)
            """)
    Page<StockItem> findFiltered(
            @Param("vendorId") UUID vendorId,
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId,
            @Param("stockStatus") StockStatus stockStatus,
            Pageable pageable
    );

    // --- Analytics queries ---

    long countByStockStatus(StockStatus stockStatus);

    @Query("SELECT COALESCE(SUM(si.quantityOnHand), 0) FROM StockItem si")
    long sumTotalQuantityOnHand();

    @Query("SELECT COALESCE(SUM(si.quantityReserved), 0) FROM StockItem si")
    long sumTotalQuantityReserved();

    // Vendor-specific
    @Query("SELECT COUNT(si) FROM StockItem si WHERE si.vendorId = :vendorId")
    long countByVendorId(@Param("vendorId") UUID vendorId);

    @Query("SELECT COUNT(si) FROM StockItem si WHERE si.vendorId = :vendorId AND si.stockStatus = :status")
    long countByVendorIdAndStockStatus(@Param("vendorId") UUID vendorId, @Param("status") StockStatus status);

    // Low stock alerts
    @Query("SELECT si FROM StockItem si WHERE si.stockStatus IN (com.rumal.inventory_service.entity.StockStatus.LOW_STOCK, com.rumal.inventory_service.entity.StockStatus.OUT_OF_STOCK) ORDER BY si.quantityAvailable ASC")
    List<StockItem> findLowStockAlerts(Pageable pageable);
}
