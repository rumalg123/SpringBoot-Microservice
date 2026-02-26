package com.rumal.wishlist_service.repo;

import com.rumal.wishlist_service.entity.WishlistCollection;
import com.rumal.wishlist_service.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);

    Page<WishlistItem> findByKeycloakId(String keycloakId, Pageable pageable);

    List<WishlistItem> findByCollectionOrderByCreatedAtDesc(WishlistCollection collection);

    @Query("SELECT i FROM WishlistItem i WHERE i.collection.id IN :collectionIds ORDER BY i.createdAt DESC")
    List<WishlistItem> findByCollectionIdInOrderByCreatedAtDesc(@Param("collectionIds") Collection<UUID> collectionIds);

    Optional<WishlistItem> findByIdAndKeycloakId(UUID id, String keycloakId);

    Optional<WishlistItem> findByKeycloakIdAndProductId(String keycloakId, UUID productId);

    Optional<WishlistItem> findByCollectionAndProductId(WishlistCollection collection, UUID productId);

    long countByCollection(WishlistCollection collection);

    long deleteByKeycloakId(String keycloakId);

    long deleteByCollection(WishlistCollection collection);

    // --- Analytics queries ---

    @Query("SELECT COUNT(DISTINCT wi.keycloakId) FROM WishlistItem wi")
    long countDistinctCustomers();

    @Query("SELECT COUNT(DISTINCT wi.productId) FROM WishlistItem wi")
    long countDistinctProducts();

    @Query("SELECT wi.productId, wi.productName, COUNT(wi) FROM WishlistItem wi GROUP BY wi.productId, wi.productName ORDER BY COUNT(wi) DESC")
    List<Object[]> findMostWishedProducts(org.springframework.data.domain.Pageable pageable);
}
