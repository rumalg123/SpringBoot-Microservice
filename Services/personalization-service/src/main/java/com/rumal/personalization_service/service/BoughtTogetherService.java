package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.model.CoPurchase;
import com.rumal.personalization_service.repository.CoPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class BoughtTogetherService {

    private final CoPurchaseRepository coPurchaseRepository;
    private final ProductClient productClient;

    @Cacheable(cacheNames = "boughtTogether", key = "#productId + '::' + #limit")
    public List<ProductSummary> getBoughtTogether(UUID productId, int limit) {
        List<CoPurchase> coPurchases = coPurchaseRepository.findByProductId(productId, PageRequest.of(0, limit));

        if (coPurchases.isEmpty()) return List.of();

        List<UUID> relatedIds = coPurchases.stream()
                .map(cp -> cp.getProductIdA().equals(productId) ? cp.getProductIdB() : cp.getProductIdA())
                .distinct()
                .collect(Collectors.toList());

        return productClient.getBatchSummaries(relatedIds);
    }
}
