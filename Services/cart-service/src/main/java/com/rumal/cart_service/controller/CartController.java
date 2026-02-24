package com.rumal.cart_service.controller;

import com.rumal.cart_service.dto.AddCartItemRequest;
import com.rumal.cart_service.dto.CartResponse;
import com.rumal.cart_service.dto.CheckoutCartRequest;
import com.rumal.cart_service.dto.CheckoutPreviewRequest;
import com.rumal.cart_service.dto.CheckoutPreviewResponse;
import com.rumal.cart_service.dto.CheckoutResponse;
import com.rumal.cart_service.dto.UpdateCartItemRequest;
import com.rumal.cart_service.dto.UpdateCartNoteRequest;
import com.rumal.cart_service.exception.UnauthorizedException;
import com.rumal.cart_service.security.InternalRequestVerifier;
import com.rumal.cart_service.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/me")
    public CartResponse getMyCart(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.getByKeycloakId(validatedSub);
    }

    @PostMapping("/me/items")
    @ResponseStatus(HttpStatus.OK)
    public CartResponse addItem(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.addItem(validatedSub, request);
    }

    @PutMapping("/me/items/{itemId}")
    public CartResponse updateItem(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.updateItem(validatedSub, itemId, request);
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
        String validatedSub = requireUserSub(userSub);
        cartService.removeItem(validatedSub, itemId);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        cartService.clear(validatedSub);
    }

    @PostMapping("/me/items/{itemId}/save-for-later")
    public CartResponse saveForLater(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.saveForLater(validatedSub, itemId);
    }

    @PostMapping("/me/items/{itemId}/move-to-cart")
    public CartResponse moveToCart(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID itemId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.moveToCart(validatedSub, itemId);
    }

    @PutMapping("/me/note")
    public CartResponse updateNote(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody UpdateCartNoteRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.updateNote(validatedSub, request);
    }

    @PostMapping("/me/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutCartRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.checkout(validatedSub, request, idempotencyKey);
    }

    @PostMapping("/me/checkout/preview")
    public CheckoutPreviewResponse previewCheckout(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CheckoutPreviewRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return cartService.previewCheckout(validatedSub, request);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }
}
