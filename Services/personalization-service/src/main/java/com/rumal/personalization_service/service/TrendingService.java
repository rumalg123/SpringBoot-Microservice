package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class TrendingService {

    private final UserEventRepository userEventRepository;
    private final ProductClient productClient;

    @Value("${personalization.trending-window-hours:48}")
    private int trendingWindowHours;

    @Cacheable(cacheNames = "trending", key = "#limit")
    public List<ProductSummary> getTrending(int limit) {
        Instant since = Instant.now().minus(trendingWindowHours, ChronoUnit.HOURS);
        List<Object[]> rows = userEventRepository.findTrendingProducts(since, PageRequest.of(0, limit));

        if (rows.isEmpty()) return List.of();

        List<UUID> productIds = rows.stream()
                .map(row -> (UUID) row[0])
                .toList();

        return productClient.getBatchSummaries(productIds);
    }
}
