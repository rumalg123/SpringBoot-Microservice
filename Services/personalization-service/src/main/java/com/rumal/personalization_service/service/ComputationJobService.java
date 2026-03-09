package com.rumal.personalization_service.service;

import com.rumal.personalization_service.model.CoPurchase;
import com.rumal.personalization_service.model.ProductSimilarity;
import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.CoPurchaseRepository;
import com.rumal.personalization_service.repository.ProductSimilarityRepository;
import com.rumal.personalization_service.repository.UserAffinityRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class ComputationJobService {

    private final UserEventRepository userEventRepository;
    private final CoPurchaseRepository coPurchaseRepository;
    private final ProductSimilarityRepository productSimilarityRepository;
    private final UserAffinityRepository userAffinityRepository;
    private final AnonymousSessionRepository anonymousSessionRepository;
    private final TrendingService trendingService;
    private final CacheManager cacheManager;

    @Value("${personalization.event-retention-days:90}")
    private int eventRetentionDays;

    @Value("${personalization.anonymous-session-expiry-days:30}")
    private int anonymousSessionExpiryDays;

    @Value("${personalization.computation.co-purchase-max-events:500000}")
    private int coPurchaseMaxEvents;

    @Value("${personalization.computation.affinity-max-aggregates:500000}")
    private int affinityMaxAggregates;

    @Value("${personalization.computation.co-purchase-lookback-days:90}")
    private int coPurchaseLookbackDays;

    @Value("${personalization.computation.similarity-lookback-days:30}")
    private int similarityLookbackDays;

    @Value("${personalization.computation.affinity-lookback-days:30}")
    private int affinityLookbackDays;

    private static record ProductFeatures(UUID productId, Set<String> categories, UUID vendorId, String brandName) {}

    private static record AffinityMetrics(double score, long eventCount) {}

    @Scheduled(cron = "${personalization.computation.co-purchase-cron:0 0 */6 * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeCoPurchases() {
        log.info("Starting co-purchase computation");
        Instant since = Instant.now().minus(coPurchaseLookbackDays, ChronoUnit.DAYS);
        Map<String, Set<UUID>> purchaseGroups = loadPurchaseGroups(since);
        Map<com.rumal.personalization_service.model.CoPurchaseId, Integer> pairCounts = countCoPurchasePairs(purchaseGroups);
        int upsertedCount = upsertCoPurchases(pairCounts, Instant.now());

        log.info("Co-purchase computation complete: {} pairs upserted from {} purchase groups", upsertedCount, purchaseGroups.size());
    }

    @Scheduled(cron = "${personalization.computation.similarity-cron:0 30 */6 * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeProductSimilarity() {
        log.info("Starting product similarity computation");
        Instant since = Instant.now().minus(similarityLookbackDays, ChronoUnit.DAYS);
        List<Object[]> productData = userEventRepository.findProductsWithRecentActivity(since, 3);

        if (productData.isEmpty()) {
            log.info("No products with recent activity for similarity computation");
            return;
        }

        Map<UUID, ProductFeatures> featureMap = buildProductFeatureMap(productData);
        List<UUID> productIds = limitSimilarityProducts(featureMap.keySet());
        Map<com.rumal.personalization_service.model.ProductSimilarityId, Double> similarityScores =
                computeSimilarityScores(productIds, featureMap);
        int upsertedCount = upsertSimilarityScores(similarityScores, Instant.now());

        log.info("Product similarity computation complete: {} similarity entries computed for {} products", upsertedCount, productIds.size());
    }

    private double computeSimilarityScore(ProductFeatures a, ProductFeatures b) {
        double categoryScore = 0;
        if (!a.categories().isEmpty() && !b.categories().isEmpty()) {
            Set<String> intersection = new HashSet<>(a.categories());
            intersection.retainAll(b.categories());
            Set<String> union = new HashSet<>(a.categories());
            union.addAll(b.categories());
            categoryScore = (double) intersection.size() / union.size();
        }

        double brandBonus = 0;
        if (a.brandName() != null && a.brandName().equalsIgnoreCase(b.brandName())) {
            brandBonus = 0.2;
        }

        double vendorBonus = 0;
        if (a.vendorId() != null && a.vendorId().equals(b.vendorId())) {
            vendorBonus = 0.1;
        }

        return Math.min(1.0, categoryScore * 0.7 + brandBonus + vendorBonus);
    }

    @Scheduled(cron = "${personalization.computation.affinity-cron:0 0 * * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeUserAffinities() {
        log.info("Starting user affinity computation");
        Instant since = Instant.now().minus(affinityLookbackDays, ChronoUnit.DAYS);
        List<Object[]> aggregates = userEventRepository.findUserEventAggregates(
                since, PageRequest.of(0, affinityMaxAggregates));

        Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores = buildUserScores(aggregates);
        Map<com.rumal.personalization_service.model.UserAffinityId, AffinityMetrics> normalizedAffinities =
                normalizeAffinities(userScores);
        int upsertedCount = upsertUserAffinities(normalizedAffinities, Instant.now());

        log.info("User affinity computation complete: {} affinities upserted for {} users", upsertedCount, userScores.size());
    }

    @Scheduled(cron = "${personalization.computation.trending-cron:0 */30 * * * *}")
    public void refreshTrendingCache() {
        Cache cache = cacheManager.getCache("trending");
        if (cache != null) {
            cache.clear();
        }

        for (int limit : List.of(8, 20, 50, 100)) {
            try {
                trendingService.getTrending(limit);
            } catch (Exception ex) {
                log.warn("Failed warming trending cache for limit {}: {}", limit, ex.getMessage());
            }
        }

        log.info("Trending cache refreshed and warmed");
    }

    @Scheduled(cron = "${personalization.computation.cleanup-cron:0 0 3 * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 120)
    @CacheEvict(cacheNames = {"similarProducts", "boughtTogether"}, allEntries = true)
    public void cleanup() {
        log.info("Starting scheduled cleanup");

        Instant eventCutoff = Instant.now().minus(eventRetentionDays, ChronoUnit.DAYS);
        int deletedEvents = userEventRepository.deleteByCreatedAtBefore(eventCutoff);

        Instant sessionCutoff = Instant.now().minus(anonymousSessionExpiryDays, ChronoUnit.DAYS);
        int deletedSessions = anonymousSessionRepository.deleteStaleUnmergedSessions(sessionCutoff);

        Instant similarityCutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deletedSimilarities = productSimilarityRepository.deleteStaleEntries(similarityCutoff);
        int deletedCoPurchases = coPurchaseRepository.deleteStaleEntries(similarityCutoff);

        log.info("Cleanup complete: {} events, {} sessions, {} similarities, {} co-purchases deleted",
                deletedEvents, deletedSessions, deletedSimilarities, deletedCoPurchases);
    }

    private Map<String, Set<UUID>> loadPurchaseGroups(Instant since) {
        List<Object[]> purchaseEvents = userEventRepository.findPurchaseEventsForCoPurchase(
                since, PageRequest.of(0, coPurchaseMaxEvents));
        Map<String, Set<UUID>> purchaseGroups = new LinkedHashMap<>();

        for (Object[] row : purchaseEvents) {
            UUID userId = (UUID) row[0];
            UUID productId = (UUID) row[1];
            Instant createdAt = (Instant) row[2];
            long windowKey = createdAt.toEpochMilli() / (5 * 60 * 1000L);
            String groupKey = userId + "::" + windowKey;
            purchaseGroups.computeIfAbsent(groupKey, ignored -> new LinkedHashSet<>()).add(productId);
        }

        return purchaseGroups;
    }

    private Map<com.rumal.personalization_service.model.CoPurchaseId, Integer> countCoPurchasePairs(Map<String, Set<UUID>> purchaseGroups) {
        Map<com.rumal.personalization_service.model.CoPurchaseId, Integer> pairCounts = new HashMap<>();

        for (Set<UUID> products : purchaseGroups.values()) {
            if (products.size() >= 2) {
                List<UUID> productList = new ArrayList<>(products);
                for (int i = 0; i < productList.size(); i++) {
                    for (int j = i + 1; j < productList.size(); j++) {
                        com.rumal.personalization_service.model.CoPurchaseId pairId =
                                orderedCoPurchaseId(productList.get(i), productList.get(j));
                        pairCounts.merge(pairId, 1, Integer::sum);
                    }
                }
            }
        }

        return pairCounts;
    }

    private int upsertCoPurchases(Map<com.rumal.personalization_service.model.CoPurchaseId, Integer> pairCounts, Instant now) {
        List<com.rumal.personalization_service.model.CoPurchaseId> pairIds = new ArrayList<>(pairCounts.keySet());
        Map<com.rumal.personalization_service.model.CoPurchaseId, CoPurchase> existingMap = coPurchaseRepository.findAllById(pairIds)
                .stream()
                .collect(Collectors.toMap(
                        entry -> new com.rumal.personalization_service.model.CoPurchaseId(entry.getProductIdA(), entry.getProductIdB()),
                        entry -> entry));

        List<CoPurchase> toSave = new ArrayList<>();
        for (Map.Entry<com.rumal.personalization_service.model.CoPurchaseId, Integer> entry : pairCounts.entrySet()) {
            com.rumal.personalization_service.model.CoPurchaseId pairId = entry.getKey();
            Integer count = entry.getValue();
            CoPurchase existing = existingMap.get(pairId);
            if (existing != null) {
                existing.setCoPurchaseCount(count);
                existing.setLastComputedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(CoPurchase.builder()
                        .productIdA(pairId.getProductIdA())
                        .productIdB(pairId.getProductIdB())
                        .coPurchaseCount(count)
                        .lastComputedAt(now)
                        .build());
            }
        }

        coPurchaseRepository.saveAll(toSave);
        return toSave.size();
    }

    private com.rumal.personalization_service.model.CoPurchaseId orderedCoPurchaseId(UUID firstProductId, UUID secondProductId) {
        if (firstProductId.compareTo(secondProductId) < 0) {
            return new com.rumal.personalization_service.model.CoPurchaseId(firstProductId, secondProductId);
        }
        return new com.rumal.personalization_service.model.CoPurchaseId(secondProductId, firstProductId);
    }

    private Map<UUID, ProductFeatures> buildProductFeatureMap(List<Object[]> productData) {
        Map<UUID, ProductFeatures> featureMap = new HashMap<>();
        for (Object[] row : productData) {
            UUID productId = (UUID) row[0];
            String categorySlugs = (String) row[1];
            UUID vendorId = (UUID) row[2];
            String brandName = (String) row[3];
            featureMap.put(productId, new ProductFeatures(productId, parseCategories(categorySlugs), vendorId, brandName));
        }
        return featureMap;
    }

    private Set<String> parseCategories(String categorySlugs) {
        if (categorySlugs == null) {
            return Set.of();
        }

        return Arrays.stream(categorySlugs.split(","))
                .map(String::trim)
                .filter(category -> !category.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<UUID> limitSimilarityProducts(Collection<UUID> productIds) {
        List<UUID> limitedProductIds = new ArrayList<>(productIds);
        if (limitedProductIds.size() > 500) {
            log.warn("Limiting similarity computation from {} to 500 products", limitedProductIds.size());
            return new ArrayList<>(limitedProductIds.subList(0, 500));
        }
        return limitedProductIds;
    }

    private Map<com.rumal.personalization_service.model.ProductSimilarityId, Double> computeSimilarityScores(
            List<UUID> productIds,
            Map<UUID, ProductFeatures> featureMap
    ) {
        Map<com.rumal.personalization_service.model.ProductSimilarityId, Double> similarityScores = new HashMap<>();

        for (UUID productId : productIds) {
            ProductFeatures baseFeatures = featureMap.get(productId);
            List<Map.Entry<UUID, Double>> rankedMatches = rankSimilarProducts(productId, productIds, baseFeatures, featureMap);
            int matchLimit = Math.min(20, rankedMatches.size());
            for (int index = 0; index < matchLimit; index++) {
                Map.Entry<UUID, Double> entry = rankedMatches.get(index);
                similarityScores.put(
                        new com.rumal.personalization_service.model.ProductSimilarityId(productId, entry.getKey()),
                        entry.getValue()
                );
            }
        }

        return similarityScores;
    }

    private List<Map.Entry<UUID, Double>> rankSimilarProducts(
            UUID baseProductId,
            List<UUID> productIds,
            ProductFeatures baseFeatures,
            Map<UUID, ProductFeatures> featureMap
    ) {
        List<Map.Entry<UUID, Double>> scores = new ArrayList<>();
        for (UUID candidateProductId : productIds) {
            if (!baseProductId.equals(candidateProductId)) {
                double score = computeSimilarityScore(baseFeatures, featureMap.get(candidateProductId));
                if (score > 0.1) {
                    scores.add(Map.entry(candidateProductId, score));
                }
            }
        }

        scores.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
        return scores;
    }

    private int upsertSimilarityScores(
            Map<com.rumal.personalization_service.model.ProductSimilarityId, Double> similarityScores,
            Instant now
    ) {
        List<com.rumal.personalization_service.model.ProductSimilarityId> similarityIds = new ArrayList<>(similarityScores.keySet());
        Map<com.rumal.personalization_service.model.ProductSimilarityId, ProductSimilarity> existingMap =
                productSimilarityRepository.findAllById(similarityIds).stream()
                        .collect(Collectors.toMap(
                                entry -> new com.rumal.personalization_service.model.ProductSimilarityId(
                                        entry.getProductId(),
                                        entry.getSimilarProductId()
                                ),
                                entry -> entry));

        List<ProductSimilarity> toSave = new ArrayList<>();
        for (Map.Entry<com.rumal.personalization_service.model.ProductSimilarityId, Double> entry : similarityScores.entrySet()) {
            com.rumal.personalization_service.model.ProductSimilarityId similarityId = entry.getKey();
            double score = entry.getValue();
            ProductSimilarity existing = existingMap.get(similarityId);
            if (existing != null) {
                existing.setScore(score);
                existing.setLastComputedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(ProductSimilarity.builder()
                        .productId(similarityId.getProductId())
                        .similarProductId(similarityId.getSimilarProductId())
                        .score(score)
                        .lastComputedAt(now)
                        .build());
            }
        }

        productSimilarityRepository.saveAll(toSave);
        return toSave.size();
    }

    private Map<UUID, Map<String, Map<String, AffinityMetrics>>> buildUserScores(List<Object[]> aggregates) {
        Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores = new HashMap<>();

        for (Object[] row : aggregates) {
            UUID userId = (UUID) row[0];
            String eventType = (String) row[1];
            String categorySlugs = (String) row[2];
            String brandName = (String) row[3];
            long eventCount = ((Number) row[4]).longValue();
            AffinityMetrics weightedMetrics = new AffinityMetrics(eventCount * eventWeight(eventType), eventCount);

            mergeCategoryAffinities(userScores, userId, categorySlugs, weightedMetrics);
            mergeBrandAffinity(userScores, userId, brandName, weightedMetrics);
        }

        return userScores;
    }

    private void mergeCategoryAffinities(
            Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores,
            UUID userId,
            String categorySlugs,
            AffinityMetrics weightedMetrics
    ) {
        if (categorySlugs == null || categorySlugs.isEmpty()) {
            return;
        }

        for (String category : categorySlugs.split(",")) {
            String normalizedCategory = category.trim();
            if (!normalizedCategory.isEmpty()) {
                mergeAffinityMetrics(userScores, userId, "CATEGORY", normalizedCategory, weightedMetrics);
            }
        }
    }

    private void mergeBrandAffinity(
            Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores,
            UUID userId,
            String brandName,
            AffinityMetrics weightedMetrics
    ) {
        if (brandName != null && !brandName.isEmpty()) {
            mergeAffinityMetrics(userScores, userId, "BRAND", brandName, weightedMetrics);
        }
    }

    private void mergeAffinityMetrics(
            Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores,
            UUID userId,
            String affinityType,
            String affinityKey,
            AffinityMetrics metrics
    ) {
        userScores
                .computeIfAbsent(userId, ignored -> new HashMap<>())
                .computeIfAbsent(affinityType, ignored -> new HashMap<>())
                .merge(affinityKey, metrics, (left, right) -> new AffinityMetrics(
                        left.score() + right.score(),
                        left.eventCount() + right.eventCount()
                ));
    }

    private double eventWeight(String eventType) {
        return switch (eventType) {
            case "PURCHASE" -> 10.0;
            case "WISHLIST_ADD" -> 5.0;
            case "ADD_TO_CART" -> 3.0;
            default -> 1.0;
        };
    }

    private Map<com.rumal.personalization_service.model.UserAffinityId, AffinityMetrics> normalizeAffinities(
            Map<UUID, Map<String, Map<String, AffinityMetrics>>> userScores
    ) {
        Map<com.rumal.personalization_service.model.UserAffinityId, AffinityMetrics> normalizedAffinities = new HashMap<>();

        for (Map.Entry<UUID, Map<String, Map<String, AffinityMetrics>>> userEntry : userScores.entrySet()) {
            UUID userId = userEntry.getKey();
            for (Map.Entry<String, Map<String, AffinityMetrics>> typeEntry : userEntry.getValue().entrySet()) {
                String affinityType = typeEntry.getKey();
                Map<String, AffinityMetrics> keyScores = typeEntry.getValue();
                double maxScore = keyScores.values().stream().mapToDouble(AffinityMetrics::score).max().orElse(1.0);
                if (maxScore == 0) {
                    maxScore = 1.0;
                }

                for (Map.Entry<String, AffinityMetrics> keyEntry : keyScores.entrySet()) {
                    AffinityMetrics metrics = keyEntry.getValue();
                    normalizedAffinities.put(
                            new com.rumal.personalization_service.model.UserAffinityId(userId, affinityType, keyEntry.getKey()),
                            new AffinityMetrics(metrics.score() / maxScore, metrics.eventCount())
                    );
                }
            }
        }

        return normalizedAffinities;
    }

    private int upsertUserAffinities(
            Map<com.rumal.personalization_service.model.UserAffinityId, AffinityMetrics> normalizedAffinities,
            Instant now
    ) {
        List<com.rumal.personalization_service.model.UserAffinityId> affinityIds = new ArrayList<>(normalizedAffinities.keySet());
        Map<com.rumal.personalization_service.model.UserAffinityId, UserAffinity> existingMap =
                userAffinityRepository.findAllById(affinityIds).stream()
                        .collect(Collectors.toMap(
                                entry -> new com.rumal.personalization_service.model.UserAffinityId(
                                        entry.getUserId(),
                                        entry.getAffinityType(),
                                        entry.getAffinityKey()
                                ),
                                entry -> entry));

        List<UserAffinity> toSave = new ArrayList<>();
        for (Map.Entry<com.rumal.personalization_service.model.UserAffinityId, AffinityMetrics> entry : normalizedAffinities.entrySet()) {
            com.rumal.personalization_service.model.UserAffinityId affinityId = entry.getKey();
            AffinityMetrics metrics = entry.getValue();
            UserAffinity existing = existingMap.get(affinityId);
            if (existing != null) {
                existing.setScore(metrics.score());
                existing.setEventCount((int) metrics.eventCount());
                existing.setLastUpdatedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(UserAffinity.builder()
                        .userId(affinityId.getUserId())
                        .affinityType(affinityId.getAffinityType())
                        .affinityKey(affinityId.getAffinityKey())
                        .score(metrics.score())
                        .eventCount((int) metrics.eventCount())
                        .lastUpdatedAt(now)
                        .build());
            }
        }

        userAffinityRepository.saveAll(toSave);
        return toSave.size();
    }
}
