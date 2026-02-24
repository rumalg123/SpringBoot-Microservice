package com.rumal.cart_service.scheduler;

import com.rumal.cart_service.repo.CartRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CartExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CartExpiryScheduler.class);

    private final CartRepository cartRepository;

    @Value("${cart.expiry.ttl:30d}")
    private Duration expiryTtl;

    @Scheduled(cron = "${cart.expiry.cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredCarts() {
        Instant cutoff = Instant.now().minus(expiryTtl);
        int deleted = cartRepository.deleteExpiredCarts(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired carts older than {}", deleted, cutoff);
        }
    }
}
