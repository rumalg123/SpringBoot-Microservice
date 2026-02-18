package com.rumal.customer_service.service;


import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
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

    @Override
    public CustomerResponse getByEmail(String email) {
        String normalized = email.trim().toLowerCase();

        Customer c = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + normalized));

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
    public CustomerResponse getById(UUID id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        return toResponse(c);
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getCreatedAt());
    }



}
