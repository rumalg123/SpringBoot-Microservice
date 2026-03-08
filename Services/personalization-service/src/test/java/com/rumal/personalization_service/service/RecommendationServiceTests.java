package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.model.UserAffinity;
import com.rumal.personalization_service.repository.UserAffinityRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    private final UserAffinityRepository userAffinityRepository = mock(UserAffinityRepository.class);
    private final UserEventRepository userEventRepository = mock(UserEventRepository.class);
    private final TrendingService trendingService = mock(TrendingService.class);
    private final RecommendationProfileService recommendationProfileService = mock(RecommendationProfileService.class);

    private final RecommendationService recommendationService = new RecommendationService(
            userAffinityRepository,
            userEventRepository,
            trendingService,
            recommendationProfileService
    );

    @Test
    void getRecommendationsForAnonymousUsesRedisSessionProfileBeforeDatabase() {
        String sessionId = "anon-session";

        when(recommendationProfileService.getTopSessionCategories(sessionId, 10))
                .thenReturn(List.of("electronics"));
        when(trendingService.getTrending(4))
                .thenReturn(List.of(
                        product("fashion", "Moda"),
                        product("electronics", "Acme")
                ));

        List<ProductSummary> result = recommendationService.getRecommendationsForAnonymous(sessionId, 2);

        assertEquals(2, result.size());
        assertTrue(result.get(0).categories().contains("electronics"));
        verify(userEventRepository, never()).findRecentCategorySlugsBySession(any(), any(), any());
    }

    @Test
    void getRecommendationsForUserFallsBackToPersistentAffinityWhenRedisProfileIsEmpty() {
        UUID userId = UUID.randomUUID();

        when(recommendationProfileService.getTopUserCategories(userId, 5)).thenReturn(List.of());
        when(recommendationProfileService.getTopUserBrands(userId, 3)).thenReturn(List.of());
        when(recommendationProfileService.getRecentPurchasedProductIds(eq(userId), any()))
                .thenReturn(Set.of());
        when(userAffinityRepository.findByUserIdAndAffinityTypeOrderByScoreDesc(eq(userId), eq("CATEGORY"), any()))
                .thenReturn(List.of(UserAffinity.builder()
                        .userId(userId)
                        .affinityType("CATEGORY")
                        .affinityKey("electronics")
                        .score(1.0)
                        .eventCount(3)
                        .lastUpdatedAt(Instant.now())
                        .build()));
        when(userAffinityRepository.findByUserIdAndAffinityTypeOrderByScoreDesc(eq(userId), eq("BRAND"), any()))
                .thenReturn(List.of(UserAffinity.builder()
                        .userId(userId)
                        .affinityType("BRAND")
                        .affinityKey("Acme")
                        .score(1.0)
                        .eventCount(2)
                        .lastUpdatedAt(Instant.now())
                        .build()));
        when(userEventRepository.findRecentPurchasedProductIds(eq(userId), any()))
                .thenReturn(List.of());
        when(trendingService.getTrending(6))
                .thenReturn(List.of(
                        product("fashion", "Moda"),
                        product("electronics", "Acme"),
                        product("electronics", "Other")
                ));

        List<ProductSummary> result = recommendationService.getRecommendationsForUser(userId, 2);

        assertEquals(2, result.size());
        assertEquals("Acme", result.get(0).brandName());
        verify(userAffinityRepository).findByUserIdAndAffinityTypeOrderByScoreDesc(eq(userId), eq("CATEGORY"), any());
        verify(userAffinityRepository).findByUserIdAndAffinityTypeOrderByScoreDesc(eq(userId), eq("BRAND"), any());
    }

    private ProductSummary product(String category, String brandName) {
        return new ProductSummary(
                UUID.randomUUID(),
                "slug",
                "Product",
                "Short",
                brandName,
                null,
                BigDecimal.valueOf(100),
                null,
                BigDecimal.valueOf(90),
                "SKU",
                category,
                Set.of(category),
                Set.of(category),
                "SIMPLE",
                "APPROVED",
                UUID.randomUUID(),
                0L,
                0L,
                true,
                List.of(),
                10,
                "IN_STOCK",
                false
        );
    }
}
