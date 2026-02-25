package com.rumal.customer_service.service;

import com.rumal.customer_service.dto.analytics.*;
import com.rumal.customer_service.entity.Customer;
import com.rumal.customer_service.exception.ResourceNotFoundException;
import com.rumal.customer_service.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CustomerAnalyticsService {

    private final CustomerRepository customerRepository;

    public CustomerPlatformSummary getPlatformSummary() {
        long total = customerRepository.count();
        long active = customerRepository.countByActiveTrue();

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        long newThisMonth = customerRepository.countNewCustomersSince(startOfMonth);

        Map<String, Long> loyaltyDist = new LinkedHashMap<>();
        for (Object[] row : customerRepository.countByLoyaltyTier()) {
            loyaltyDist.put(row[0].toString(), ((Number) row[1]).longValue());
        }

        return new CustomerPlatformSummary(total, active, newThisMonth, loyaltyDist);
    }

    public List<MonthlyGrowthBucket> getGrowthTrend(int months) {
        Instant since = LocalDate.now(ZoneOffset.UTC).minusMonths(months).withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Object[]> rows = customerRepository.countNewCustomersByMonth(since);
        // We don't have a simple way to get running totalActive per month, so we return newCustomers only
        // totalActive is approximated as current active count
        long currentActive = customerRepository.countByActiveTrue();
        return rows.stream()
            .map(r -> new MonthlyGrowthBucket((String) r[0], ((Number) r[1]).longValue(), currentActive))
            .toList();
    }

    public CustomerProfileSummary getProfileSummary(UUID customerId) {
        Customer c = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        return new CustomerProfileSummary(c.getId(), c.getName(), c.getEmail(),
            c.getLoyaltyTier().name(), c.getLoyaltyPoints(), c.getCreatedAt(), c.isActive());
    }
}
