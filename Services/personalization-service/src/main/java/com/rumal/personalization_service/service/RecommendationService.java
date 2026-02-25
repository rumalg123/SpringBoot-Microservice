package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.repository.UserAffinityRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class RecommendationService {

    private final UserAffinityRepository userAffinityRepository;
    private final UserEventRepository userEventRepository;
    private final TrendingService trendingService;

    @Value("${personalization.recommendation-limit:20}")
    private int defaultLimit;

    @Cacheable(cacheNames = "recommendations", key = "'user::' + #userId + '::' + #limit")
    public List<ProductSummary> getRecommendationsForUser(UUID userId, int limit) {
        List<UserAffinity> categoryAffinities = userAffinityRepository
                .findByUserIdAndAffinityTypeOrderByScoreDesc(userId, "CATEGORY", PageRequest.of(0, 5));
        List<UserAffinity> brandAffinities = userAffinityRepository
                .findByUserIdAndAffinityTypeOrderByScoreDesc(userId, "BRAND", PageRequest.of(0, 3));

        Set<UUID> excludeIds = new HashSet<>(
                userEventRepository.findRecentPurchasedProductIds(userId, Instant.now().minus(30, ChronoUnit.DAYS))
        );

        Set<UUID> candidateIds = new LinkedHashSet<>();

        List<String> categories = categoryAffinities.stream().map(UserAffinity::getAffinityKey).toList();
        List<String> brands = brandAffinities.stream().map(UserAffinity::getAffinityKey).toList();

        // Get trending products and filter by user's preferred categories/brands
        List<ProductSummary> trending = trendingService.getTrending(limit * 3);
        for (ProductSummary p : trending) {
            if (excludeIds.contains(p.id())) continue;
            boolean matchesCategory = p.categories() != null && categories.stream().anyMatch(c -> p.categories().contains(c));
            boolean matchesBrand = brands.contains(p.brandName());
            if (matchesCategory || matchesBrand) {
                candidateIds.add(p.id());
            }
        }

        // Fill remaining with general trending
        if (candidateIds.size() < limit) {
            for (ProductSummary p : trending) {
                if (candidateIds.size() >= limit) break;
                if (!excludeIds.contains(p.id())) {
                    candidateIds.add(p.id());
                }
            }
        }

        if (candidateIds.isEmpty()) return List.of();

        Map<UUID, ProductSummary> productMap = trending.stream()
                .collect(Collectors.toMap(ProductSummary::id, p -> p, (a, b) -> a));
        return candidateIds.stream()
                .limit(limit)
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Cacheable(cacheNames = "recommendations", key = "'anon::' + #sessionId + '::' + #limit")
    public List<ProductSummary> getRecommendationsForAnonymous(String sessionId, int limit) {
        List<String> recentCategories = userEventRepository.findRecentCategorySlugsBySession(
                sessionId, Instant.now().minus(7, ChronoUnit.DAYS), PageRequest.of(0, 10));

        Set<String> preferredCategories = recentCategories.stream()
                .flatMap(slugs -> Arrays.stream(slugs.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ProductSummary> trending = trendingService.getTrending(limit * 2);

        if (preferredCategories.isEmpty()) {
            return trending.stream().limit(limit).toList();
        }

        List<ProductSummary> matched = new ArrayList<>();
        List<ProductSummary> rest = new ArrayList<>();

        for (ProductSummary p : trending) {
            boolean matches = p.categories() != null &&
                    preferredCategories.stream().anyMatch(c -> p.categories().contains(c));
            if (matches) matched.add(p);
            else rest.add(p);
        }

        List<ProductSummary> result = new ArrayList<>(matched);
        result.addAll(rest);
        return result.stream().limit(limit).toList();
    }
}
