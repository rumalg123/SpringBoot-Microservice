package com.rumal.personalization_service.service;

import com.rumal.personalization_service.model.CoPurchase;
import com.rumal.personalization_service.model.ProductSimilarity;
import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
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
public class ComputationJobService {

    private final UserEventRepository userEventRepository;
    private final CoPurchaseRepository coPurchaseRepository;
    private final ProductSimilarityRepository productSimilarityRepository;
    private final UserAffinityRepository userAffinityRepository;
    private final AnonymousSessionRepository anonymousSessionRepository;

    @Value("${personalization.event-retention-days:90}")
    private int eventRetentionDays;

    @Value("${personalization.anonymous-session-expiry-days:30}")
    private int anonymousSessionExpiryDays;

    @Value("${personalization.computation.co-purchase-max-events:500000}")
    private int coPurchaseMaxEvents;

    @Value("${personalization.computation.affinity-max-aggregates:500000}")
    private int affinityMaxAggregates;

    private static record ProductFeatures(UUID productId, Set<String> categories, UUID vendorId, String brandName) {}

    // ---- Co-purchase computation (every 6h) ----

    @Scheduled(cron = "${personalization.computation.co-purchase-cron:0 0 */6 * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeCoPurchases() {
        log.info("Starting co-purchase computation");
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Object[]> purchaseEvents = userEventRepository.findPurchaseEventsForCoPurchase(
                since, PageRequest.of(0, coPurchaseMaxEvents));

        // Group by userId and 5-minute purchase windows
        Map<String, Set<UUID>> purchaseGroups = new LinkedHashMap<>();
        for (Object[] row : purchaseEvents) {
            UUID userId = (UUID) row[0];
            UUID productId = (UUID) row[1];
            Instant createdAt = (Instant) row[2];
            // 5-minute window key
            long windowKey = createdAt.toEpochMilli() / (5 * 60 * 1000);
            String groupKey = userId + "::" + windowKey;
            purchaseGroups.computeIfAbsent(groupKey, k -> new LinkedHashSet<>()).add(productId);
        }

        // Generate pairs and count
        Map<String, Integer> pairCounts = new HashMap<>();
        for (Set<UUID> products : purchaseGroups.values()) {
            if (products.size() < 2) continue;
            List<UUID> productList = new ArrayList<>(products);
            for (int i = 0; i < productList.size(); i++) {
                for (int j = i + 1; j < productList.size(); j++) {
                    UUID a = productList.get(i);
                    UUID b = productList.get(j);
                    // Ensure consistent ordering
                    String pairKey = a.compareTo(b) < 0 ? a + "::" + b : b + "::" + a;
                    pairCounts.merge(pairKey, 1, Integer::sum);
                }
            }
        }

        // Batch-fetch existing entries, then upsert all at once
        Instant now = Instant.now();
        List<com.rumal.personalization_service.model.CoPurchaseId> allPairIds = new ArrayList<>();
        Map<com.rumal.personalization_service.model.CoPurchaseId, Integer> pairIdToCount = new HashMap<>();
        for (Map.Entry<String, Integer> entry : pairCounts.entrySet()) {
            String[] ids = entry.getKey().split("::");
            UUID idA = UUID.fromString(ids[0]);
            UUID idB = UUID.fromString(ids[1]);
            var id = new com.rumal.personalization_service.model.CoPurchaseId(idA, idB);
            allPairIds.add(id);
            pairIdToCount.put(id, entry.getValue());
        }

        Map<com.rumal.personalization_service.model.CoPurchaseId, CoPurchase> existingMap = coPurchaseRepository.findAllById(allPairIds)
                .stream().collect(Collectors.toMap(
                        cp -> new com.rumal.personalization_service.model.CoPurchaseId(cp.getProductIdA(), cp.getProductIdB()),
                        cp -> cp));

        List<CoPurchase> toSave = new ArrayList<>();
        for (var idEntry : pairIdToCount.entrySet()) {
            var id = idEntry.getKey();
            int count = idEntry.getValue();
            CoPurchase existing = existingMap.get(id);
            if (existing != null) {
                existing.setCoPurchaseCount(count);
                existing.setLastComputedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(CoPurchase.builder()
                        .productIdA(id.getProductIdA())
                        .productIdB(id.getProductIdB())
                        .coPurchaseCount(count)
                        .lastComputedAt(now)
                        .build());
            }
        }
        coPurchaseRepository.saveAll(toSave);

        log.info("Co-purchase computation complete: {} pairs upserted from {} purchase groups", toSave.size(), purchaseGroups.size());
    }

    // ---- Product similarity computation (every 6h) ----

    @Scheduled(cron = "${personalization.computation.similarity-cron:0 30 */6 * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeProductSimilarity() {
        log.info("Starting product similarity computation");
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> productData = userEventRepository.findProductsWithRecentActivity(since, 3);

        if (productData.isEmpty()) {
            log.info("No products with recent activity for similarity computation");
            return;
        }

        // Build product feature map
        Map<UUID, ProductFeatures> featureMap = new HashMap<>();

        for (Object[] row : productData) {
            UUID productId = (UUID) row[0];
            String categorySlugs = (String) row[1];
            UUID vendorId = (UUID) row[2];
            String brandName = (String) row[3];

            Set<String> categories = categorySlugs != null
                    ? Arrays.stream(categorySlugs.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet())
                    : Set.of();

            featureMap.put(productId, new ProductFeatures(productId, categories, vendorId, brandName));
        }

        Instant now = Instant.now();
        List<UUID> productIds = new ArrayList<>(featureMap.keySet());
        if (productIds.size() > 500) {
            log.warn("Limiting similarity computation from {} to 500 products", productIds.size());
            productIds = new ArrayList<>(productIds.subList(0, 500));
        }

        // Compute all similarity pairs in memory first
        Map<com.rumal.personalization_service.model.ProductSimilarityId, Double> newScores = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            UUID productA = productIds.get(i);
            ProductFeatures featA = featureMap.get(productA);

            List<Map.Entry<UUID, Double>> scores = new ArrayList<>();
            for (int j = 0; j < productIds.size(); j++) {
                if (i == j) continue;
                UUID productB = productIds.get(j);
                ProductFeatures featB = featureMap.get(productB);

                double score = computeSimilarityScore(featA, featB);
                if (score > 0.1) {
                    scores.add(Map.entry(productB, score));
                }
            }

            scores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int limit = Math.min(20, scores.size());
            for (int k = 0; k < limit; k++) {
                Map.Entry<UUID, Double> entry = scores.get(k);
                newScores.put(new com.rumal.personalization_service.model.ProductSimilarityId(productA, entry.getKey()), entry.getValue());
            }
        }

        // Batch-fetch existing, merge, and saveAll
        List<com.rumal.personalization_service.model.ProductSimilarityId> allIds = new ArrayList<>(newScores.keySet());
        Map<com.rumal.personalization_service.model.ProductSimilarityId, ProductSimilarity> existingMap =
                productSimilarityRepository.findAllById(allIds).stream()
                        .collect(Collectors.toMap(
                                ps -> new com.rumal.personalization_service.model.ProductSimilarityId(ps.getProductId(), ps.getSimilarProductId()),
                                ps -> ps));

        List<ProductSimilarity> toSave = new ArrayList<>();
        for (var entry : newScores.entrySet()) {
            var id = entry.getKey();
            double score = entry.getValue();
            ProductSimilarity existing = existingMap.get(id);
            if (existing != null) {
                existing.setScore(score);
                existing.setLastComputedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(ProductSimilarity.builder()
                        .productId(id.getProductId())
                        .similarProductId(id.getSimilarProductId())
                        .score(score)
                        .lastComputedAt(now)
                        .build());
            }
        }
        productSimilarityRepository.saveAll(toSave);

        log.info("Product similarity computation complete: {} similarity entries computed for {} products", toSave.size(), productIds.size());
    }

    private double computeSimilarityScore(ProductFeatures a, ProductFeatures b) {
        // Jaccard similarity for categories
        double categoryScore = 0;
        if (!a.categories().isEmpty() && !b.categories().isEmpty()) {
            Set<String> intersection = new HashSet<>(a.categories());
            intersection.retainAll(b.categories());
            Set<String> union = new HashSet<>(a.categories());
            union.addAll(b.categories());
            categoryScore = (double) intersection.size() / union.size();
        }

        // Brand match bonus
        double brandBonus = 0;
        if (a.brandName() != null && a.brandName().equalsIgnoreCase(b.brandName())) {
            brandBonus = 0.2;
        }

        // Vendor match bonus
        double vendorBonus = 0;
        if (a.vendorId() != null && a.vendorId().equals(b.vendorId())) {
            vendorBonus = 0.1;
        }

        return Math.min(1.0, categoryScore * 0.7 + brandBonus + vendorBonus);
    }

    // ---- User affinity computation (every 1h) ----

    @Scheduled(cron = "${personalization.computation.affinity-cron:0 0 * * * *}")
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
    public void computeUserAffinities() {
        log.info("Starting user affinity computation");
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> aggregates = userEventRepository.findUserEventAggregates(
                since, PageRequest.of(0, affinityMaxAggregates));

        // Group by userId → affinityType → affinityKey → weighted score
        Map<UUID, Map<String, Map<String, double[]>>> userScores = new HashMap<>();

        for (Object[] row : aggregates) {
            UUID userId = (UUID) row[0];
            String eventType = (String) row[1];
            String categorySlugs = (String) row[2];
            String brandName = (String) row[3];
            long count = ((Number) row[4]).longValue();

            double weight = switch (eventType) {
                case "PURCHASE" -> 10.0;
                case "WISHLIST_ADD" -> 5.0;
                case "ADD_TO_CART" -> 3.0;
                default -> 1.0;
            };

            double weightedScore = count * weight;

            // Category affinities
            if (categorySlugs != null && !categorySlugs.isEmpty()) {
                for (String cat : categorySlugs.split(",")) {
                    String trimmed = cat.trim();
                    if (!trimmed.isEmpty()) {
                        userScores
                                .computeIfAbsent(userId, k -> new HashMap<>())
                                .computeIfAbsent("CATEGORY", k -> new HashMap<>())
                                .merge(trimmed, new double[]{weightedScore, count}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
                    }
                }
            }

            // Brand affinities
            if (brandName != null && !brandName.isEmpty()) {
                userScores
                        .computeIfAbsent(userId, k -> new HashMap<>())
                        .computeIfAbsent("BRAND", k -> new HashMap<>())
                        .merge(brandName, new double[]{weightedScore, count}, (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
            }
        }

        // Normalize and collect all affinity entries to upsert
        Instant now = Instant.now();
        Map<com.rumal.personalization_service.model.UserAffinityId, double[]> normalizedAffinities = new HashMap<>();

        for (Map.Entry<UUID, Map<String, Map<String, double[]>>> userEntry : userScores.entrySet()) {
            UUID userId = userEntry.getKey();
            for (Map.Entry<String, Map<String, double[]>> typeEntry : userEntry.getValue().entrySet()) {
                String affinityType = typeEntry.getKey();
                Map<String, double[]> keyScores = typeEntry.getValue();

                double maxScore = keyScores.values().stream().mapToDouble(v -> v[0]).max().orElse(1.0);
                if (maxScore == 0) maxScore = 1.0;

                for (Map.Entry<String, double[]> keyEntry : keyScores.entrySet()) {
                    double normalizedScore = keyEntry.getValue()[0] / maxScore;
                    int eventCount = (int) keyEntry.getValue()[1];
                    var id = new com.rumal.personalization_service.model.UserAffinityId(userId, affinityType, keyEntry.getKey());
                    normalizedAffinities.put(id, new double[]{normalizedScore, eventCount});
                }
            }
        }

        // Batch-fetch existing, merge, and saveAll
        List<com.rumal.personalization_service.model.UserAffinityId> allIds = new ArrayList<>(normalizedAffinities.keySet());
        Map<com.rumal.personalization_service.model.UserAffinityId, UserAffinity> existingMap =
                userAffinityRepository.findAllById(allIds).stream()
                        .collect(Collectors.toMap(
                                ua -> new com.rumal.personalization_service.model.UserAffinityId(ua.getUserId(), ua.getAffinityType(), ua.getAffinityKey()),
                                ua -> ua));

        List<UserAffinity> toSave = new ArrayList<>();
        for (var entry : normalizedAffinities.entrySet()) {
            var id = entry.getKey();
            double normalizedScore = entry.getValue()[0];
            int eventCount = (int) entry.getValue()[1];
            UserAffinity existing = existingMap.get(id);
            if (existing != null) {
                existing.setScore(normalizedScore);
                existing.setEventCount(eventCount);
                existing.setLastUpdatedAt(now);
                toSave.add(existing);
            } else {
                toSave.add(UserAffinity.builder()
                        .userId(id.getUserId())
                        .affinityType(id.getAffinityType())
                        .affinityKey(id.getAffinityKey())
                        .score(normalizedScore)
                        .eventCount(eventCount)
                        .lastUpdatedAt(now)
                        .build());
            }
        }
        userAffinityRepository.saveAll(toSave);

        log.info("User affinity computation complete: {} affinities upserted for {} users", toSave.size(), userScores.size());
    }

    // ---- Trending cache refresh (every 30m) ----

    @Scheduled(cron = "${personalization.computation.trending-cron:0 */30 * * * *}")
    @CacheEvict(cacheNames = "trending", allEntries = true)
    public void refreshTrendingCache() {
        log.info("Trending cache evicted for refresh");
    }

    // ---- Cleanup job (daily 3 AM) ----

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
}
