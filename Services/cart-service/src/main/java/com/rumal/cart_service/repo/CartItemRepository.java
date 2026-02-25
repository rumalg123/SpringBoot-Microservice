package com.rumal.cart_service.repo;

import com.rumal.cart_service.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    // --- Analytics queries ---

    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.savedForLater = false")
    long countActiveCartItems();

    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.savedForLater = true")
    long countSavedForLaterItems();

    @Query("SELECT COALESCE(AVG(ci.lineTotal), 0) FROM CartItem ci WHERE ci.savedForLater = false")
    java.math.BigDecimal avgCartItemValue();
}
