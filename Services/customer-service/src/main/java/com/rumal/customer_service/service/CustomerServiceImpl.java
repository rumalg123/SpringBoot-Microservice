package com.rumal.customer_service.service;

import com.rumal.customer_service.auth.KeycloakManagementService;
import com.rumal.customer_service.auth.KeycloakUserExistsException;
import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.entity.Customer;
import com.rumal.customer_service.exception.DuplicateResourceException;
import com.rumal.customer_service.exception.ResourceNotFoundException;
import com.rumal.customer_service.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final KeycloakManagementService keycloakManagementService;

    @Override
    public CustomerResponse getByEmail(String email) {
        String normalized = email.trim().toLowerCase();

        Customer c = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + normalized));

        return toResponse(c);
    }

    @Override
    @Cacheable(cacheNames = "customerByKeycloak", key = "#keycloakId")
    public CustomerResponse getByKeycloakId(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new ResourceNotFoundException("Customer not found for keycloak id");
        }

        Customer c = customerRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));

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

        String keycloakId;
        try {
            keycloakId = keycloakManagementService.createUser(email, request.password(), request.name().trim());
        } catch (KeycloakUserExistsException ex) {
            keycloakId = keycloakManagementService.getUserIdByEmail(email);
        }

        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(request.name().trim())
                        .email(email)
                        .keycloakId(keycloakId)
                        .build()
        );

        return toResponse(saved);
    }

    @Override
    @CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId")
    public CustomerResponse registerIdentity(String keycloakId, String email, RegisterIdentityCustomerRequest request) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new ResourceNotFoundException("Customer not found for keycloak id");
        }
        String resolvedEmail = email;
        String resolvedName = request != null ? request.name() : null;

        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            var user = keycloakManagementService.getUserById(keycloakId);
            resolvedEmail = user.email();
            if (resolvedName == null || resolvedName.isBlank()) {
                resolvedName = user.name();
            }
        }

        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new ResourceNotFoundException("Customer email is required");
        }

        String normalizedEmail = resolvedEmail.trim().toLowerCase();

        Customer existingByKeycloak = customerRepository.findByKeycloakId(keycloakId).orElse(null);
        if (existingByKeycloak != null) {
            return toResponse(existingByKeycloak);
        }

        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Customer already exists with email: " + normalizedEmail);
        }

        String name = (resolvedName != null && !resolvedName.isBlank())
                ? resolvedName.trim()
                : normalizedEmail;

        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(name)
                        .email(normalizedEmail)
                        .keycloakId(keycloakId)
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
