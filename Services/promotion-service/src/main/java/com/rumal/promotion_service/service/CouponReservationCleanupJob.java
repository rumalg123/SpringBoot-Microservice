package com.rumal.promotion_service.service;

import com.rumal.promotion_service.repo.CouponReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CouponReservationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CouponReservationCleanupJob.class);

    private final CouponReservationRepository couponReservationRepository;

    @Scheduled(fixedDelayString = "${coupon.reservation.cleanup-interval-ms:60000}")
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public void expireStaleReservations() {
        try {
            int expired = couponReservationRepository.expireStaleReservations(Instant.now());
            if (expired > 0) {
                log.info("Expired {} stale coupon reservations", expired);
            }
        } catch (Exception ex) {
            log.error("Failed to expire stale coupon reservations", ex);
        }
    }
}
