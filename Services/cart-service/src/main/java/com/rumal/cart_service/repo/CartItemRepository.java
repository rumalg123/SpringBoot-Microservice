package com.rumal.cart_service.repo;

import com.rumal.cart_service.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    // --- Analytics queries ---

    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.savedForLater = false AND ci.cart.createdAt >= :since")
    long countActiveCartItems(@Param("since") Instant since);

    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.savedForLater = true AND ci.cart.createdAt >= :since")
    long countSavedForLaterItems(@Param("since") Instant since);

    @Query("SELECT COALESCE(AVG(ci.lineTotal), 0) FROM CartItem ci WHERE ci.savedForLater = false AND ci.cart.createdAt >= :since")
    java.math.BigDecimal avgCartItemValue(@Param("since") Instant since);
}
