package com.rumal.customer_service.service;



import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;

import java.util.UUID;

public interface CustomerService {
    CustomerResponse getByEmail(String email);

    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse getById(UUID id);
}
