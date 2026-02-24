package com.rumal.cart_service.repo;

import com.rumal.cart_service.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("select distinct c from Cart c left join fetch c.items where c.keycloakId = :keycloakId")
    Optional<Cart> findWithItemsByKeycloakId(@Param("keycloakId") String keycloakId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct c from Cart c left join fetch c.items where c.keycloakId = :keycloakId")
    Optional<Cart> findWithItemsByKeycloakIdForUpdate(@Param("keycloakId") String keycloakId);

    @Modifying
    @Query("delete from Cart c where c.lastActivityAt < :cutoff")
    int deleteExpiredCarts(@Param("cutoff") Instant cutoff);
}
