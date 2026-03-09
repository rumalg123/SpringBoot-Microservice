package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.repository.UserAffinityRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final RecommendationProfileService recommendationProfileService;

    @Cacheable(cacheNames = "recommendations", key = "'user::' + #userId + '::' + #limit")
    public List<ProductSummary> getRecommendationsForUser(UUID userId, int limit) {
        List<String> categories = resolvePreferredAffinities(userId, "CATEGORY", 5);
        List<String> brands = resolvePreferredAffinities(userId, "BRAND", 3);
        Instant purchaseCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Set<UUID> excludedProductIds = resolveExcludedProductIds(userId, purchaseCutoff);
        List<ProductSummary> trendingProducts = trendingService.getTrending(limit * 3);
        Set<UUID> candidateIds = collectPreferredCandidateIds(trendingProducts, categories, brands, excludedProductIds);

        fillWithTrendingFallback(candidateIds, trendingProducts, excludedProductIds, limit);
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProductSummary> productMap = trendingProducts.stream()
                .collect(Collectors.toMap(ProductSummary::id, product -> product, (left, right) -> left));
        return candidateIds.stream()
                .limit(limit)
                .map(productMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Cacheable(cacheNames = "recommendations", key = "'anon::' + #sessionId + '::' + #limit")
    public List<ProductSummary> getRecommendationsForAnonymous(String sessionId, int limit) {
        Set<String> preferredCategories = recommendationProfileService.getTopSessionCategories(sessionId, 10).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
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

    private List<String> resolvePreferredAffinities(UUID userId, String affinityType, int limit) {
        List<String> profileAffinities = "CATEGORY".equals(affinityType)
                ? recommendationProfileService.getTopUserCategories(userId, limit)
                : recommendationProfileService.getTopUserBrands(userId, limit);

        if (!profileAffinities.isEmpty()) {
            return profileAffinities;
        }

        return userAffinityRepository
                .findByUserIdAndAffinityTypeOrderByScoreDesc(userId, affinityType, PageRequest.of(0, limit))
                .stream()
                .map(UserAffinity::getAffinityKey)
                .toList();
    }

    private Set<UUID> resolveExcludedProductIds(UUID userId, Instant purchaseCutoff) {
        Set<UUID> excludedProductIds = new HashSet<>(recommendationProfileService.getRecentPurchasedProductIds(userId, purchaseCutoff));
        if (excludedProductIds.isEmpty()) {
            excludedProductIds.addAll(userEventRepository.findRecentPurchasedProductIds(userId, purchaseCutoff));
        }
        return excludedProductIds;
    }

    private Set<UUID> collectPreferredCandidateIds(
            List<ProductSummary> trendingProducts,
            List<String> categories,
            List<String> brands,
            Set<UUID> excludedProductIds
    ) {
        Set<UUID> candidateIds = new LinkedHashSet<>();
        for (ProductSummary product : trendingProducts) {
            boolean excluded = excludedProductIds.contains(product.id());
            boolean matchesPreference = !excluded && (matchesPreferredCategory(product, categories) || matchesPreferredBrand(product, brands));
            if (matchesPreference) {
                candidateIds.add(product.id());
            }
        }
        return candidateIds;
    }

    private boolean matchesPreferredCategory(ProductSummary product, List<String> categories) {
        return product.categories() != null && categories.stream().anyMatch(category -> product.categories().contains(category));
    }

    private boolean matchesPreferredBrand(ProductSummary product, List<String> brands) {
        return brands.contains(product.brandName());
    }

    private void fillWithTrendingFallback(
            Set<UUID> candidateIds,
            List<ProductSummary> trendingProducts,
            Set<UUID> excludedProductIds,
            int limit
    ) {
        if (candidateIds.size() >= limit) {
            return;
        }

        for (ProductSummary product : trendingProducts) {
            if (candidateIds.size() >= limit) {
                return;
            }
            if (!excludedProductIds.contains(product.id())) {
                candidateIds.add(product.id());
            }
        }
    }
}
