package com.rumal.customer_service.service;



import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterCustomerRequest;

import java.util.UUID;

public interface CustomerService {
    CustomerResponse getByEmail(String email);
    CustomerResponse getByAuth0Id(String auth0Id);

    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse register(RegisterCustomerRequest request);
    CustomerResponse getById(UUID id);
}
