package com.rumal.wishlist_service.controller;

import com.rumal.wishlist_service.dto.AddWishlistItemRequest;
import com.rumal.wishlist_service.dto.CreateWishlistCollectionRequest;
import com.rumal.wishlist_service.dto.SharedWishlistResponse;
import com.rumal.wishlist_service.dto.UpdateItemNoteRequest;
import com.rumal.wishlist_service.dto.UpdateWishlistCollectionRequest;
import com.rumal.wishlist_service.dto.WishlistCollectionResponse;
import com.rumal.wishlist_service.dto.WishlistItemResponse;
import com.rumal.wishlist_service.dto.WishlistResponse;
import com.rumal.wishlist_service.exception.UnauthorizedException;
import com.rumal.wishlist_service.security.InternalRequestVerifier;
import com.rumal.wishlist_service.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private final InternalRequestVerifier internalRequestVerifier;

    // ──────────────────────────────────────────────────────────────────────────
    //  Legacy flat-wishlist endpoints (backwards-compatible)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public Page<WishlistItemResponse> getMyWishlist(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.getByKeycloakId(keycloakId, pageable);
    }

    @PostMapping("/me/items")
    @ResponseStatus(HttpStatus.OK)
    public WishlistResponse addItem(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody AddWishlistItemRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.addItem(keycloakId, request);
    }

    @DeleteMapping("/me/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.removeItem(keycloakId, itemId);
    }

    @DeleteMapping("/me/items/by-product/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByProduct(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID productId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.removeByProductId(keycloakId, productId);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.clear(keycloakId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Collection management
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/me/collections")
    public List<WishlistCollectionResponse> getCollections(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.getCollections(keycloakId);
    }

    @PostMapping("/me/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistCollectionResponse createCollection(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CreateWishlistCollectionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.createCollection(keycloakId, request);
    }

    @PutMapping("/me/collections/{collectionId}")
    public WishlistCollectionResponse updateCollection(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID collectionId,
            @Valid @RequestBody UpdateWishlistCollectionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.updateCollection(keycloakId, collectionId, request);
    }

    @DeleteMapping("/me/collections/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID collectionId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.deleteCollection(keycloakId, collectionId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Sharing
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/me/collections/{collectionId}/share")
    public WishlistCollectionResponse enableSharing(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID collectionId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.enableSharing(keycloakId, collectionId);
    }

    @DeleteMapping("/me/collections/{collectionId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSharing(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID collectionId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.revokeSharing(keycloakId, collectionId);
    }

    @GetMapping("/shared/{shareToken}")
    public SharedWishlistResponse getSharedWishlist(@PathVariable String shareToken) {
        return wishlistService.getSharedWishlist(shareToken);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Move to cart
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/me/items/{itemId}/move-to-cart")
    @ResponseStatus(HttpStatus.OK)
    public void moveItemToCart(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        wishlistService.moveItemToCart(keycloakId, itemId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Item notes
    // ──────────────────────────────────────────────────────────────────────────

    @PutMapping("/me/items/{itemId}/note")
    public WishlistItemResponse updateItemNote(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemNoteRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String keycloakId = requireUserSub(userSub);
        return wishlistService.updateItemNote(keycloakId, itemId, request);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub;
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }
}
