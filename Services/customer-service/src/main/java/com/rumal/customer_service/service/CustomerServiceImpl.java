package com.rumal.customer_service.service;


import com.rumal.customer_service.auth.Auth0ManagementService;
import com.rumal.customer_service.auth.Auth0UserExistsException;
import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.entity.Customer;
import com.rumal.customer_service.exception.DuplicateResourceException;
import com.rumal.customer_service.exception.ResourceNotFoundException;
import com.rumal.customer_service.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final Auth0ManagementService auth0ManagementService;

    @Override
    public CustomerResponse getByEmail(String email) {
        String normalized = email.trim().toLowerCase();

        Customer c = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + normalized));

        return toResponse(c);
    }

    @Override
    public CustomerResponse getByAuth0Id(String auth0Id) {
        if (auth0Id == null || auth0Id.isBlank()) {
            throw new ResourceNotFoundException("Customer not found for auth0 id");
        }

        Customer c = customerRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for auth0 id"));

        return toResponse(c);
    }

    @Override
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = request.email().trim().toLowerCase();

        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Customer already exists with email: " + email);
        }

        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(request.name().trim())
                        .email(email)
                        .build()
        );

        return toResponse(saved);
    }

    @Override
    public CustomerResponse register(RegisterCustomerRequest request) {
        String email = request.email().trim().toLowerCase();

        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Customer already exists with email: " + email);
        }

        String auth0Id;
        try {
            auth0Id = auth0ManagementService.createUser(email, request.password(), request.name().trim());
        } catch (Auth0UserExistsException ex) {
            auth0Id = auth0ManagementService.getUserIdByEmail(email);
        }

        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(request.name().trim())
                        .email(email)
                        .auth0Id(auth0Id)
                        .build()
        );

        return toResponse(saved);
    }

    @Override
    public CustomerResponse getById(UUID id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        return toResponse(c);
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getCreatedAt());
    }



}
