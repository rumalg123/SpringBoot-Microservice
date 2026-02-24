package com.rumal.order_service.repo;

import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> withFilters(
            UUID customerId,
            UUID vendorId,
            OrderStatus status,
            Instant createdAfter,
            Instant createdBefore
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (vendorId != null) {
                var itemJoin = root.join("orderItems");
                predicates.add(cb.equal(itemJoin.get("vendorId"), vendorId));
                query.distinct(true);
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (createdAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            }
            if (createdBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
