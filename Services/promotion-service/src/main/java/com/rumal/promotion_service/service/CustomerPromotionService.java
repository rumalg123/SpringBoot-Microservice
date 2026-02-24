package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.CouponUsageResponse;
import com.rumal.promotion_service.entity.CouponReservationStatus;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CustomerPromotionService {

    private final CouponReservationRepository couponReservationRepository;

    public Page<CouponUsageResponse> getUsageHistory(UUID customerId, Pageable pageable) {
        return couponReservationRepository
                .findByCustomerIdAndStatusWithPromotion(customerId, CouponReservationStatus.COMMITTED, pageable)
                .map(reservation -> new CouponUsageResponse(
                        reservation.getId(),
                        reservation.getCouponCodeText(),
                        reservation.getCouponCode() != null && reservation.getCouponCode().getPromotion() != null
                                ? reservation.getCouponCode().getPromotion().getName()
                                : null,
                        reservation.getReservedDiscountAmount(),
                        reservation.getOrderId(),
                        reservation.getCommittedAt()
                ));
    }
}
