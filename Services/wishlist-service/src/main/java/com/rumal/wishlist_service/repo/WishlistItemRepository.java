package com.rumal.wishlist_service.repo;

import com.rumal.wishlist_service.entity.WishlistCollection;
import com.rumal.wishlist_service.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);

    Page<WishlistItem> findByKeycloakId(String keycloakId, Pageable pageable);

    List<WishlistItem> findByCollectionOrderByCreatedAtDesc(WishlistCollection collection);

    Optional<WishlistItem> findByIdAndKeycloakId(UUID id, String keycloakId);

    Optional<WishlistItem> findByKeycloakIdAndProductId(String keycloakId, UUID productId);

    Optional<WishlistItem> findByCollectionAndProductId(WishlistCollection collection, UUID productId);

    long countByCollection(WishlistCollection collection);

    long deleteByKeycloakId(String keycloakId);

    long deleteByCollection(WishlistCollection collection);
}
