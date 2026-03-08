package com.rumal.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.dto.AnalyticsLiveDashboardMessage;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.VendorOrder;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class OrderAnalyticsLiveUpdateServiceTests {

    @Test
    void notifyOrderChangedAfterCommitClearsAnalyticsCachesAndPublishesScopedMessage() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "orderAnalyticsPlatformSummary",
                "orderAnalyticsRevenueTrend",
                "orderAnalyticsTopProducts",
                "orderAnalyticsStatusBreakdown",
                "orderAnalyticsVendorSummary",
                "orderAnalyticsVendorRevenueTrend",
                "orderAnalyticsVendorTopProducts"
        );
        cacheManager.getCache("orderAnalyticsPlatformSummary").put("platform", "stale");
        cacheManager.getCache("orderAnalyticsVendorSummary").put("vendor", "stale");

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderAnalyticsLiveUpdateService service = new OrderAnalyticsLiveUpdateService(cacheManager, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "analyticsLiveRedisChannel", "analytics:live:test");

        UUID orderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        VendorOrder vendorOrder = new VendorOrder();
        vendorOrder.setVendorId(vendorId);

        Order order = new Order();
        order.setId(orderId);
        order.setVendorOrders(List.of(vendorOrder));

        service.notifyOrderChangedAfterCommit(order, "order_created");

        assertThat(cacheManager.getCache("orderAnalyticsPlatformSummary").get("platform")).isNull();
        assertThat(cacheManager.getCache("orderAnalyticsVendorSummary").get("vendor")).isNull();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("analytics:live:test"), payloadCaptor.capture());

        AnalyticsLiveDashboardMessage actualMessage = objectMapper.readValue(payloadCaptor.getValue(), AnalyticsLiveDashboardMessage.class);
        assertThat(actualMessage.orderId()).isEqualTo(orderId);
        assertThat(actualMessage.vendorIds()).isEqualTo(Set.of(vendorId));
        assertThat(actualMessage.trigger()).isEqualTo("order_created");
        assertThat(actualMessage.occurredAt()).isNotNull();
    }
}
