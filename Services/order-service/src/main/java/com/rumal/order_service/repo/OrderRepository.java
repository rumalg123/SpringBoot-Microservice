package com.rumal.order_service.repo;


import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query(
            value = """
                    select distinct o
                    from Order o
                    join o.orderItems oi
                    where oi.vendorId = :vendorId
                    """,
            countQuery = """
                    select count(distinct o.id)
                    from Order o
                    join o.orderItems oi
                    where oi.vendorId = :vendorId
                    """
    )
    Page<Order> findByVendorId(@Param("vendorId") UUID vendorId, Pageable pageable);

    @Query(
            value = """
                    select distinct o
                    from Order o
                    join o.orderItems oi
                    where o.customerId = :customerId
                      and oi.vendorId = :vendorId
                    """,
            countQuery = """
                    select count(distinct o.id)
                    from Order o
                    join o.orderItems oi
                    where o.customerId = :customerId
                      and oi.vendorId = :vendorId
                    """
    )
    Page<Order> findByCustomerIdAndVendorId(
            @Param("customerId") UUID customerId,
            @Param("vendorId") UUID vendorId,
            Pageable pageable
    );

    @Query("""
            select count(distinct o.id)
            from Order o
            join o.orderItems oi
            where oi.vendorId = :vendorId
              and o.status in :statuses
            """)
    long countDistinctOrdersByVendorIdAndStatuses(
            @Param("vendorId") UUID vendorId,
            @Param("statuses") Collection<OrderStatus> statuses
    );

    @Query("""
            select count(distinct o.id)
            from Order o
            join o.orderItems oi
            where oi.vendorId = :vendorId
            """)
    long countDistinctOrdersByVendorId(@Param("vendorId") UUID vendorId);

    @Query("""
            select max(o.createdAt)
            from Order o
            join o.orderItems oi
            where oi.vendorId = :vendorId
            """)
    Instant findLatestOrderCreatedAtByVendorId(@Param("vendorId") UUID vendorId);
}
