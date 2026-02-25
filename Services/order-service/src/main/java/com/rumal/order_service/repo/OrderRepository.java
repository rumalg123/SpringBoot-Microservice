package com.rumal.order_service.repo;


import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

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

    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND o.expiresAt IS NOT NULL AND o.expiresAt < :now")
    List<Order> findExpiredOrders(@Param("statuses") Collection<OrderStatus> statuses, @Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT o.id FROM Order o JOIN o.orderItems oi
            WHERE o.customerId = :customerId AND oi.productId = :productId
            AND o.status IN (com.rumal.order_service.entity.OrderStatus.DELIVERED, com.rumal.order_service.entity.OrderStatus.CLOSED)
            ORDER BY o.updatedAt DESC
            """)
    List<UUID> findDeliveredOrderIdsByCustomerAndProduct(@Param("customerId") UUID customerId, @Param("productId") UUID productId);
}
