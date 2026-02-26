package com.rumal.cart_service.service;

import com.rumal.cart_service.dto.analytics.CartPlatformSummary;
import com.rumal.cart_service.repo.CartItemRepository;
import com.rumal.cart_service.repo.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CartAnalyticsService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartPlatformSummary getPlatformSummary() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long activeCarts = cartRepository.countActiveCarts();
        long cartItems = cartItemRepository.countActiveCartItems(since);
        long savedForLater = cartItemRepository.countSavedForLaterItems(since);
        BigDecimal avgValue = cartItemRepository.avgCartItemValue(since);
        double avgItems = activeCarts > 0 ? (double) cartItems / activeCarts : 0.0;

        return new CartPlatformSummary(activeCarts, cartItems, savedForLater,
            avgValue, Math.round(avgItems * 100.0) / 100.0);
    }
}
