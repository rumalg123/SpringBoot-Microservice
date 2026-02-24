package com.rumal.customer_service.controller;


import com.rumal.customer_service.dto.AddLoyaltyPointsRequest;
import com.rumal.customer_service.dto.CommunicationPreferencesResponse;
import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerActivityLogResponse;
import com.rumal.customer_service.dto.CustomerAddressRequest;
import com.rumal.customer_service.dto.CustomerAddressResponse;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.LinkedAccountsResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.dto.UpdateCommunicationPreferencesRequest;
import com.rumal.customer_service.dto.UpdateCustomerProfileRequest;
import com.rumal.customer_service.exception.UnauthorizedException;
import com.rumal.customer_service.security.InternalRequestVerifier;
import com.rumal.customer_service.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return customerService.create(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        return customerService.register(request);
    }

    @PostMapping("/register-identity")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse registerIdentity(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody(required = false) RegisterIdentityCustomerRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.registerIdentity(validatedSub, userEmail, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return customerService.getById(id);
    }

    @GetMapping("/me")
    public CustomerResponse me(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.getByKeycloakId(validatedSub);
    }

    @PutMapping("/me")
    public CustomerResponse updateMe(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody UpdateCustomerProfileRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.updateProfile(validatedSub, request, extractIpAddress(httpRequest));
    }

    @PostMapping("/me/deactivate")
    public CustomerResponse deactivateMe(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.deactivateAccount(validatedSub);
    }

    @GetMapping("/by-email")
    public CustomerResponse getByEmail(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam String email
    ) {
        internalRequestVerifier.verify(internalAuth);
        return customerService.getByEmail(email);
    }

    @GetMapping("/{customerId}/addresses/{addressId}")
    public CustomerAddressResponse getAddressByCustomerId(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID customerId,
            @PathVariable UUID addressId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return customerService.getAddressByCustomerId(customerId, addressId);
    }

    @GetMapping("/me/addresses")
    public List<CustomerAddressResponse> listMyAddresses(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.listAddressesByKeycloak(validatedSub);
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressResponse addMyAddress(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CustomerAddressRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.addAddressByKeycloak(validatedSub, request, extractIpAddress(httpRequest));
    }

    @PutMapping("/me/addresses/{addressId}")
    public CustomerAddressResponse updateMyAddress(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID addressId,
            @Valid @RequestBody CustomerAddressRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.updateAddressByKeycloak(validatedSub, addressId, request, extractIpAddress(httpRequest));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAddress(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID addressId,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        customerService.softDeleteAddressByKeycloak(validatedSub, addressId, extractIpAddress(httpRequest));
    }

    @PostMapping("/me/addresses/{addressId}/default-shipping")
    public CustomerAddressResponse setDefaultShippingAddress(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID addressId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.setDefaultShippingByKeycloak(validatedSub, addressId);
    }

    @PostMapping("/me/addresses/{addressId}/default-billing")
    public CustomerAddressResponse setDefaultBillingAddress(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID addressId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.setDefaultBillingByKeycloak(validatedSub, addressId);
    }

    // ── Loyalty (internal endpoint) ──────────────────────────────────────────

    @PostMapping("/internal/{id}/add-loyalty-points")
    public CustomerResponse addLoyaltyPoints(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody AddLoyaltyPointsRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return customerService.addLoyaltyPoints(id, request.points());
    }

    // ── Communication Preferences ────────────────────────────────────────────

    @GetMapping("/me/communication-preferences")
    public CommunicationPreferencesResponse getCommunicationPreferences(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.getCommunicationPreferences(validatedSub);
    }

    @PutMapping("/me/communication-preferences")
    public CommunicationPreferencesResponse updateCommunicationPreferences(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody UpdateCommunicationPreferencesRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.updateCommunicationPreferences(validatedSub, request);
    }

    // ── Activity Log ─────────────────────────────────────────────────────────

    @GetMapping("/me/activity-log")
    public Page<CustomerActivityLogResponse> getActivityLog(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.getActivityLog(validatedSub, pageable);
    }

    // ── Social Login Linking ─────────────────────────────────────────────────

    @GetMapping("/me/linked-accounts")
    public LinkedAccountsResponse getLinkedAccounts(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return customerService.getLinkedAccounts(validatedSub);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
