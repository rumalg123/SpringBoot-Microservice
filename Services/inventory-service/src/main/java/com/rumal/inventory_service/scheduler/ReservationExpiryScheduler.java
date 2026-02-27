package com.rumal.inventory_service.scheduler;

import com.rumal.inventory_service.service.StockService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final StockService stockService;

    @Scheduled(fixedDelayString = "${inventory.reservation.cleanup-interval:PT1M}", initialDelayString = "PT30S")
    public void cleanupExpiredReservations() {
        int totalExpired = 0;
        try {
            int batch;
            do {
                batch = stockService.expireStaleReservationsBatch(100);
                totalExpired += batch;
            } while (batch > 0);
        } catch (Exception e) {
            log.error("Error during reservation expiry cleanup (expired {} so far)", totalExpired, e);
        }
        if (totalExpired > 0) {
            log.info("Reservation expiry scheduler released {} expired reservations total", totalExpired);
        }
    }
}
