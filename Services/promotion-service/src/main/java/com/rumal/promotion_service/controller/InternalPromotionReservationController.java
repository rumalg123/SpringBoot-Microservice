package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.CommitCouponReservationRequest;
import com.rumal.promotion_service.dto.CouponReservationResponse;
import com.rumal.promotion_service.dto.CreateCouponReservationRequest;
import com.rumal.promotion_service.dto.ReleaseCouponReservationRequest;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.CouponReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/promotions/reservations")
@RequiredArgsConstructor
public class InternalPromotionReservationController {

    private final CouponReservationService couponReservationService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    public CouponReservationResponse reserve(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @Valid @RequestBody CreateCouponReservationRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return couponReservationService.reserve(request);
    }

    @PostMapping("/{reservationId}/commit")
    public CouponReservationResponse commit(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CommitCouponReservationRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return couponReservationService.commit(reservationId, request);
    }

    @PostMapping("/{reservationId}/release")
    public CouponReservationResponse release(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReleaseCouponReservationRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return couponReservationService.release(reservationId, request);
    }
}
