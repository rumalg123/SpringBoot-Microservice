package com.rumal.promotion_service.repo;

import com.rumal.promotion_service.entity.CouponCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponCodeRepository extends JpaRepository<CouponCode, UUID> {

    @Query("select c from CouponCode c join fetch c.promotion where upper(c.code) = upper(:code)")
    Optional<CouponCode> findByCodeWithPromotion(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CouponCode c join fetch c.promotion where upper(c.code) = upper(:code)")
    Optional<CouponCode> findByCodeWithPromotionForUpdate(@Param("code") String code);

    @Query("select c from CouponCode c join fetch c.promotion p where p.id = :promotionId order by c.createdAt desc")
    List<CouponCode> findByPromotionIdOrderByCreatedAtDesc(@Param("promotionId") UUID promotionId);

    long countByPromotion_Id(UUID promotionId);

    long countByPromotion_IdAndActiveTrue(UUID promotionId);

    boolean existsByCodeIgnoreCase(String code);

    Optional<CouponCode> findByIdAndPromotion_Id(UUID id, UUID promotionId);
}
