package com.rumal.wishlist_service.repo;

import com.rumal.wishlist_service.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);

    Optional<WishlistItem> findByIdAndKeycloakId(UUID id, String keycloakId);

    Optional<WishlistItem> findByKeycloakIdAndProductId(String keycloakId, UUID productId);

    long deleteByKeycloakId(String keycloakId);
}
