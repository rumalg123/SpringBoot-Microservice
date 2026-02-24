package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.entity.VendorOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorOrderRepository extends JpaRepository<VendorOrder, UUID> {

    List<VendorOrder> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Query("""
            select count(distinct vo.order.id)
            from VendorOrder vo
            where vo.vendorId = :vendorId
            """)
    long countDistinctParentOrdersByVendorId(@Param("vendorId") UUID vendorId);

    @Query("""
            select count(distinct vo.order.id)
            from VendorOrder vo
            where vo.vendorId = :vendorId
              and vo.status in :statuses
            """)
    long countDistinctParentOrdersByVendorIdAndStatuses(
            @Param("vendorId") UUID vendorId,
            @Param("statuses") Collection<OrderStatus> statuses
    );

    @Query("""
            select max(vo.order.createdAt)
            from VendorOrder vo
            where vo.vendorId = :vendorId
            """)
    Instant findLatestParentOrderCreatedAtByVendorId(@Param("vendorId") UUID vendorId);

    Page<VendorOrder> findByVendorIdOrderByCreatedAtDesc(UUID vendorId, Pageable pageable);

    Page<VendorOrder> findByVendorIdAndStatusOrderByCreatedAtDesc(UUID vendorId, OrderStatus status, Pageable pageable);
}
