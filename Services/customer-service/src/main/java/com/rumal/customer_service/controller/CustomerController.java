package com.rumal.customer_service.controller;


import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.dto.UpdateCustomerProfileRequest;
import com.rumal.customer_service.exception.UnauthorizedException;
import com.rumal.customer_service.security.InternalRequestVerifier;
import com.rumal.customer_service.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
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
            @RequestBody(required = false) RegisterIdentityCustomerRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return customerService.registerIdentity(userSub, userEmail, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
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
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return customerService.getByKeycloakId(userSub);
    }

    @PutMapping("/me")
    public CustomerResponse updateMe(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody UpdateCustomerProfileRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return customerService.updateProfile(userSub, request);
    }

    @GetMapping("/by-email")
    public CustomerResponse getByEmail(@RequestParam String email) {
        return customerService.getByEmail(email);
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

}
