package com.rumal.customer_service.controller;


import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.exception.UnauthorizedException;
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

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return customerService.getById(id);
    }

    @GetMapping("/me")
    public CustomerResponse me(@RequestHeader("X-Auth0-Sub") String auth0Id) {
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
