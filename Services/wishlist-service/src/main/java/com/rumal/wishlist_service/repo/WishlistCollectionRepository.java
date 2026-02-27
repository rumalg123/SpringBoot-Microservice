package com.rumal.wishlist_service.repo;

import com.rumal.wishlist_service.entity.WishlistCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistCollectionRepository extends JpaRepository<WishlistCollection, UUID> {

    List<WishlistCollection> findByKeycloakIdOrderByCreatedAtAsc(String keycloakId);

    Optional<WishlistCollection> findByIdAndKeycloakId(UUID id, String keycloakId);

    List<WishlistCollection> findByKeycloakIdAndIsDefaultTrueOrderByCreatedAtAsc(String keycloakId);

    Optional<WishlistCollection> findByShareToken(String shareToken);

    long countByKeycloakId(String keycloakId);
}
