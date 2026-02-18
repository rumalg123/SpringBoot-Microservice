package com.rumal.customer_service.controller;


import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterAuth0CustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
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

    @PostMapping("/register-auth0")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse registerAuth0(
            @RequestHeader("X-Auth0-Sub") String auth0Id,
            @RequestHeader(value = "X-Auth0-Email", required = false) String email,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestBody(required = false) RegisterAuth0CustomerRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        if (auth0Id == null || auth0Id.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return customerService.registerAuth0(auth0Id, email, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return customerService.getById(id);
    }

    @GetMapping("/me")
    public CustomerResponse me(
            @RequestHeader("X-Auth0-Sub") String auth0Id,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        if (auth0Id == null || auth0Id.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return customerService.getByAuth0Id(auth0Id);
    }

    @GetMapping("/by-email")
    public CustomerResponse getByEmail(@RequestParam String email) {
        return customerService.getByEmail(email);
    }

}
