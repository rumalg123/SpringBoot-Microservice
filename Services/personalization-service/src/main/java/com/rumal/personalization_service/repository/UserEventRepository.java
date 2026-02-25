package com.rumal.personalization_service.repository;

import com.rumal.personalization_service.model.UserEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserEventRepository extends JpaRepository<UserEvent, UUID> {

    @Query("""
            SELECT e.productId, SUM(
                CASE WHEN e.eventType = 'PURCHASE' THEN 10
                     WHEN e.eventType = 'ADD_TO_CART' THEN 3
                     WHEN e.eventType = 'WISHLIST_ADD' THEN 5
                     ELSE 1 END
            ) AS score
            FROM UserEvent e
            WHERE e.createdAt > :since
            GROUP BY e.productId
            ORDER BY SUM(
                CASE WHEN e.eventType = 'PURCHASE' THEN 10
                     WHEN e.eventType = 'ADD_TO_CART' THEN 3
                     WHEN e.eventType = 'WISHLIST_ADD' THEN 5
                     ELSE 1 END
            ) DESC
            """)
    List<Object[]> findTrendingProducts(Instant since, Pageable pageable);

    List<UserEvent> findByUserIdAndEventTypeOrderByCreatedAtDesc(UUID userId, String eventType, Pageable pageable);

    List<UserEvent> findBySessionIdAndEventTypeOrderByCreatedAtDesc(String sessionId, String eventType, Pageable pageable);

    List<UserEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<UserEvent> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    @Query("""
            SELECT e.productId FROM UserEvent e
            WHERE e.userId = :userId AND e.eventType = 'PURCHASE'
            AND e.createdAt > :since
            """)
    List<UUID> findRecentPurchasedProductIds(UUID userId, Instant since);

    @Query("""
            SELECT e.categorySlugs FROM UserEvent e
            WHERE e.sessionId = :sessionId AND e.categorySlugs IS NOT NULL
            AND e.createdAt > :since
            GROUP BY e.categorySlugs
            ORDER BY MAX(e.createdAt) DESC
            """)
    List<String> findRecentCategorySlugsBySession(String sessionId, Instant since, Pageable pageable);

    @Query("""
            SELECT e.userId, e.productId, e.createdAt FROM UserEvent e
            WHERE e.eventType = 'PURCHASE' AND e.createdAt > :since
            ORDER BY e.userId, e.createdAt
            """)
    List<Object[]> findPurchaseEventsForCoPurchase(Instant since);

    @Query("""
            SELECT e.productId, e.categorySlugs, e.vendorId, e.brandName, COUNT(e) AS cnt
            FROM UserEvent e
            WHERE e.createdAt > :since
            GROUP BY e.productId, e.categorySlugs, e.vendorId, e.brandName
            HAVING COUNT(e) >= :minEvents
            """)
    List<Object[]> findProductsWithRecentActivity(Instant since, long minEvents);

    @Query("""
            SELECT e.userId, e.eventType,
                COALESCE(e.categorySlugs, ''),
                COALESCE(e.brandName, ''),
                COUNT(e)
            FROM UserEvent e
            WHERE e.userId IS NOT NULL AND e.createdAt > :since
            GROUP BY e.userId, e.eventType, COALESCE(e.categorySlugs, ''), COALESCE(e.brandName, '')
            """)
    List<Object[]> findUserEventAggregates(Instant since);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserEvent e WHERE e.createdAt < :before")
    int deleteByCreatedAtBefore(Instant before);

    @Modifying
    @Transactional
    @Query("UPDATE UserEvent e SET e.userId = :userId WHERE e.sessionId = :sessionId AND e.userId IS NULL")
    int mergeSessionEvents(UUID userId, String sessionId);
}
