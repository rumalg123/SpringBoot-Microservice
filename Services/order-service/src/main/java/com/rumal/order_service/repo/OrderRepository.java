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

import org.springframework.data.jpa.repository.EntityGraph;

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

    @EntityGraph(attributePaths = {"orderItems", "vendorOrders"})
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
            SELECT o.id, oi.vendorId FROM Order o JOIN o.orderItems oi
            WHERE o.customerId = :customerId AND oi.productId = :productId
            AND o.status IN (com.rumal.order_service.entity.OrderStatus.DELIVERED, com.rumal.order_service.entity.OrderStatus.CLOSED)
            ORDER BY o.updatedAt DESC
            """)
    List<Object[]> findDeliveredOrderIdsByCustomerAndProduct(@Param("customerId") UUID customerId, @Param("productId") UUID productId);

    // --- Analytics queries ---

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();

    long countByStatus(OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    long countByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.orderTotal), 0) FROM Order o WHERE o.status IN :statuses")
    java.math.BigDecimal sumOrderTotalByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalDiscount), 0) FROM Order o WHERE o.status IN :statuses")
    java.math.BigDecimal sumTotalDiscountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.shippingAmount), 0) FROM Order o WHERE o.status IN :statuses")
    java.math.BigDecimal sumShippingAmountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT CAST(o.createdAt AS LocalDate), COALESCE(SUM(o.orderTotal), 0), COUNT(o) FROM Order o WHERE o.createdAt >= :since AND o.status IN :statuses GROUP BY CAST(o.createdAt AS LocalDate) ORDER BY CAST(o.createdAt AS LocalDate)")
    List<Object[]> getRevenueByDay(@Param("since") java.time.Instant since, @Param("statuses") Collection<OrderStatus> statuses);

    // Customer analytics
    @Query("SELECT COUNT(DISTINCT oi.vendorId) FROM OrderItem oi JOIN oi.order o WHERE o.customerId = :customerId")
    long countUniqueVendorsByCustomer(@Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(SUM(o.orderTotal), 0) FROM Order o WHERE o.customerId = :customerId AND o.status IN :statuses")
    java.math.BigDecimal sumCustomerSpentByStatusIn(@Param("customerId") UUID customerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalDiscount), 0) FROM Order o WHERE o.customerId = :customerId AND o.status IN :statuses")
    java.math.BigDecimal sumCustomerSavedByStatusIn(@Param("customerId") UUID customerId, @Param("statuses") Collection<OrderStatus> statuses);

    long countByCustomerId(UUID customerId);

    long countByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId = :customerId AND o.status IN :statuses")
    long countByCustomerIdAndStatusIn(@Param("customerId") UUID customerId, @Param("statuses") Collection<OrderStatus> statuses);

    // Monthly spending for customer
    @Query(value = "SELECT TO_CHAR(o.created_at, 'YYYY-MM'), COALESCE(SUM(o.order_total), 0), COUNT(*) FROM orders o WHERE o.customer_id = :customerId AND o.status IN :statuses AND o.created_at >= :since GROUP BY TO_CHAR(o.created_at, 'YYYY-MM') ORDER BY TO_CHAR(o.created_at, 'YYYY-MM')", nativeQuery = true)
    List<Object[]> getCustomerMonthlySpending(@Param("customerId") UUID customerId, @Param("statuses") Collection<String> statuses, @Param("since") java.time.Instant since);
}
