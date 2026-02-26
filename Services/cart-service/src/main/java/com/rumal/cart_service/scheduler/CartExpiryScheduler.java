package com.rumal.cart_service.scheduler;

import com.rumal.cart_service.repo.CartRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CartExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CartExpiryScheduler.class);

    private final CartRepository cartRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${cart.expiry.ttl:30d}")
    private Duration expiryTtl;

    @Value("${cart.expiry.batch-size:500}")
    private int batchSize;

    @Scheduled(cron = "${cart.expiry.cron:0 0 3 * * *}")
    public void purgeExpiredCarts() {
        try {
            Instant cutoff = Instant.now().minus(expiryTtl);
            int totalDeleted = 0;
            int deleted;
            do {
                Integer result = transactionTemplate.execute(status ->
                        cartRepository.deleteExpiredCartsBatch(cutoff, batchSize));
                deleted = result != null ? result : 0;
                totalDeleted += deleted;
            } while (deleted >= batchSize);
            if (totalDeleted > 0) {
                log.info("Purged {} expired carts older than {}", totalDeleted, cutoff);
            }
        } catch (Exception ex) {
            log.error("Failed to purge expired carts", ex);
        }
    }
}
