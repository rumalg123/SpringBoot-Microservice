package com.rumal.promotion_service.repo;

import com.rumal.promotion_service.entity.CouponReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CouponReservationRepository extends JpaRepository<CouponReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CouponReservation r join fetch r.couponCode c join fetch c.promotion where r.id = :id")
    Optional<CouponReservation> findByIdForUpdate(@Param("id") UUID id);

    Optional<CouponReservation> findByRequestKey(String requestKey);

    @Query("""
            select count(r) from CouponReservation r
            where r.couponCode.id = :couponCodeId
              and (
                    r.status = com.rumal.promotion_service.entity.CouponReservationStatus.COMMITTED
                 or (r.status = com.rumal.promotion_service.entity.CouponReservationStatus.RESERVED and r.expiresAt > :now)
              )
            """)
    long countActiveOrCommittedByCouponCodeId(@Param("couponCodeId") UUID couponCodeId, @Param("now") Instant now);

    @Query("""
            select count(r) from CouponReservation r
            where r.couponCode.id = :couponCodeId
              and r.customerId = :customerId
              and (
                    r.status = com.rumal.promotion_service.entity.CouponReservationStatus.COMMITTED
                 or (r.status = com.rumal.promotion_service.entity.CouponReservationStatus.RESERVED and r.expiresAt > :now)
              )
            """)
    long countActiveOrCommittedByCouponCodeIdAndCustomerId(
            @Param("couponCodeId") UUID couponCodeId,
            @Param("customerId") UUID customerId,
            @Param("now") Instant now
    );

    @Query("""
            select coalesce(sum(r.reservedDiscountAmount), 0)
            from CouponReservation r
            where r.promotionId = :promotionId
              and r.status = com.rumal.promotion_service.entity.CouponReservationStatus.RESERVED
              and r.expiresAt > :now
            """)
    BigDecimal sumActiveReservedDiscountByPromotionId(@Param("promotionId") UUID promotionId, @Param("now") Instant now);
}
