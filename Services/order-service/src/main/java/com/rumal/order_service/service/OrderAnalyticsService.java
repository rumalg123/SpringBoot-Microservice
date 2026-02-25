package com.rumal.order_service.service;

import com.rumal.order_service.dto.analytics.*;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.repo.OrderItemRepository;
import com.rumal.order_service.repo.OrderRepository;
import com.rumal.order_service.repo.VendorOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class OrderAnalyticsService {

    private final OrderRepository orderRepository;
    private final VendorOrderRepository vendorOrderRepository;
    private final OrderItemRepository orderItemRepository;

    private static final Set<OrderStatus> COMPLETED_STATUSES = Set.of(OrderStatus.DELIVERED, OrderStatus.CLOSED);
    private static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(
        OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED,
        OrderStatus.ON_HOLD, OrderStatus.PROCESSING, OrderStatus.SHIPPED
    );
    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
        OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
        OrderStatus.DELIVERED, OrderStatus.CLOSED
    );

    public PlatformOrderSummary getPlatformSummary(int periodDays) {
        long total = orderRepository.count();
        long pending = orderRepository.countByStatus(OrderStatus.PENDING)
                     + orderRepository.countByStatus(OrderStatus.PAYMENT_PENDING);
        long processing = orderRepository.countByStatus(OrderStatus.CONFIRMED)
                        + orderRepository.countByStatus(OrderStatus.PROCESSING);
        long shipped = orderRepository.countByStatus(OrderStatus.SHIPPED);
        long delivered = orderRepository.countByStatusIn(COMPLETED_STATUSES);
        long cancelled = orderRepository.countByStatus(OrderStatus.CANCELLED);
        long refunded = orderRepository.countByStatus(OrderStatus.REFUNDED);

        BigDecimal totalRevenue = orderRepository.sumOrderTotalByStatusIn(REVENUE_STATUSES);
        BigDecimal totalDiscount = orderRepository.sumTotalDiscountByStatusIn(REVENUE_STATUSES);
        BigDecimal totalShipping = orderRepository.sumShippingAmountByStatusIn(REVENUE_STATUSES);

        long revenueOrders = orderRepository.countByStatusIn(REVENUE_STATUSES);
        BigDecimal avgOrderValue = revenueOrders > 0
            ? totalRevenue.divide(BigDecimal.valueOf(revenueOrders), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        double completionRate = total > 0 ? (double) delivered / total * 100.0 : 0.0;

        return new PlatformOrderSummary(total, pending, processing, shipped, delivered,
            cancelled, refunded, totalRevenue, totalDiscount, totalShipping,
            avgOrderValue, Math.round(completionRate * 100.0) / 100.0);
    }

    public List<DailyRevenueBucket> getRevenueTrend(int days) {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Object[]> rows = orderRepository.getRevenueByDay(since, REVENUE_STATUSES);
        return rows.stream()
            .map(r -> new DailyRevenueBucket(
                r[0] instanceof LocalDate ld ? ld : LocalDate.parse(r[0].toString()),
                (BigDecimal) r[1],
                ((Number) r[2]).longValue()))
            .toList();
    }

    public List<TopProductEntry> getTopProducts(int limit) {
        List<Object[]> rows = orderItemRepository.findTopProductsByRevenue(COMPLETED_STATUSES, PageRequest.of(0, limit));
        return rows.stream()
            .map(r -> new TopProductEntry(
                (UUID) r[0], (String) r[1], (UUID) r[2],
                ((Number) r[3]).longValue(), (BigDecimal) r[4]))
            .toList();
    }

    public Map<String, Long> getStatusBreakdown() {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = orderRepository.countByStatus(status);
            if (count > 0) breakdown.put(status.name(), count);
        }
        return breakdown;
    }

    public VendorOrderSummary getVendorSummary(UUID vendorId, int periodDays) {
        long total = vendorOrderRepository.countByVendorId(vendorId);
        long active = vendorOrderRepository.countByVendorIdAndStatusIn(vendorId, ACTIVE_STATUSES);
        long completed = vendorOrderRepository.countByVendorIdAndStatusIn(vendorId, COMPLETED_STATUSES);
        long cancelled = vendorOrderRepository.countByVendorIdAndStatus(vendorId, OrderStatus.CANCELLED);
        long refunded = vendorOrderRepository.countByVendorIdAndStatus(vendorId, OrderStatus.REFUNDED);

        BigDecimal revenue = vendorOrderRepository.sumVendorRevenueByStatusIn(vendorId, REVENUE_STATUSES);
        BigDecimal fees = vendorOrderRepository.sumVendorPlatformFeesByStatusIn(vendorId, REVENUE_STATUSES);
        BigDecimal payouts = vendorOrderRepository.sumVendorPayoutsByStatusIn(vendorId, REVENUE_STATUSES);

        long revenueOrders = vendorOrderRepository.countByVendorIdAndStatusIn(vendorId, REVENUE_STATUSES);
        BigDecimal avgValue = revenueOrders > 0
            ? revenue.divide(BigDecimal.valueOf(revenueOrders), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new VendorOrderSummary(vendorId, total, active, completed, cancelled,
            refunded, revenue, fees, payouts, avgValue);
    }

    public List<DailyRevenueBucket> getVendorRevenueTrend(UUID vendorId, int days) {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Object[]> rows = vendorOrderRepository.getVendorRevenueByDay(vendorId, since, REVENUE_STATUSES);
        return rows.stream()
            .map(r -> new DailyRevenueBucket(
                r[0] instanceof LocalDate ld ? ld : LocalDate.parse(r[0].toString()),
                (BigDecimal) r[1],
                ((Number) r[2]).longValue()))
            .toList();
    }

    public List<TopProductEntry> getVendorTopProducts(UUID vendorId, int limit) {
        List<Object[]> rows = orderItemRepository.findTopProductsByRevenueForVendor(vendorId, COMPLETED_STATUSES, PageRequest.of(0, limit));
        return rows.stream()
            .map(r -> new TopProductEntry(
                (UUID) r[0], (String) r[1], (UUID) r[2],
                ((Number) r[3]).longValue(), (BigDecimal) r[4]))
            .toList();
    }

    public CustomerOrderSummary getCustomerSummary(UUID customerId) {
        long total = orderRepository.countByCustomerId(customerId);
        long active = orderRepository.countByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES);
        long completed = orderRepository.countByCustomerIdAndStatusIn(customerId, COMPLETED_STATUSES);

        BigDecimal spent = orderRepository.sumCustomerSpentByStatusIn(customerId, REVENUE_STATUSES);
        BigDecimal saved = orderRepository.sumCustomerSavedByStatusIn(customerId, REVENUE_STATUSES);

        BigDecimal avgValue = total > 0
            ? spent.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        long uniqueVendors = orderRepository.countUniqueVendorsByCustomer(customerId);

        return new CustomerOrderSummary(customerId, total, active, completed,
            spent, saved, avgValue, uniqueVendors);
    }

    public List<MonthlySpendBucket> getCustomerSpendingTrend(UUID customerId, int months) {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusMonths(months).withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<String> statusStrings = REVENUE_STATUSES.stream().map(Enum::name).collect(Collectors.toSet());
        List<Object[]> rows = orderRepository.getCustomerMonthlySpending(customerId, statusStrings, since);
        return rows.stream()
            .map(r -> new MonthlySpendBucket(
                (String) r[0],
                r[1] instanceof BigDecimal bd ? bd : new BigDecimal(r[1].toString()),
                ((Number) r[2]).longValue()))
            .toList();
    }
}
