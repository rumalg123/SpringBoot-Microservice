package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OrderItem;
import com.rumal.order_service.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // Top products by revenue across platform (join through vendorOrder to check status)
    @Query("""
        SELECT oi.productId, oi.item, oi.vendorId, SUM(oi.quantity), SUM(oi.lineTotal)
        FROM OrderItem oi JOIN oi.vendorOrder vo
        WHERE vo.status IN :completedStatuses
        GROUP BY oi.productId, oi.item, oi.vendorId
        ORDER BY SUM(oi.lineTotal) DESC
        """)
    List<Object[]> findTopProductsByRevenue(@Param("completedStatuses") Collection<OrderStatus> completedStatuses, Pageable pageable);

    // Top products by revenue for a specific vendor
    @Query("""
        SELECT oi.productId, oi.item, oi.vendorId, SUM(oi.quantity), SUM(oi.lineTotal)
        FROM OrderItem oi JOIN oi.vendorOrder vo
        WHERE oi.vendorId = :vendorId AND vo.status IN :completedStatuses
        GROUP BY oi.productId, oi.item, oi.vendorId
        ORDER BY SUM(oi.lineTotal) DESC
        """)
    List<Object[]> findTopProductsByRevenueForVendor(@Param("vendorId") UUID vendorId,
                                                      @Param("completedStatuses") Collection<OrderStatus> completedStatuses,
                                                      Pageable pageable);
}
