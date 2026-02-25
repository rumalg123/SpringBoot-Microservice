package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.model.ProductSimilarity;
import com.rumal.personalization_service.repository.ProductSimilarityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class SimilarProductsService {

    private final ProductSimilarityRepository productSimilarityRepository;
    private final ProductClient productClient;

    @Cacheable(cacheNames = "similarProducts", key = "#productId + '::' + #limit")
    public List<ProductSummary> getSimilarProducts(UUID productId, int limit) {
        List<ProductSimilarity> similarities = productSimilarityRepository
                .findByProductIdOrderByScoreDesc(productId, PageRequest.of(0, limit));

        if (similarities.isEmpty()) {
            return List.of();
        }

        List<UUID> similarIds = similarities.stream()
                .map(ProductSimilarity::getSimilarProductId)
                .toList();

        return productClient.getBatchSummaries(similarIds);
    }
}
