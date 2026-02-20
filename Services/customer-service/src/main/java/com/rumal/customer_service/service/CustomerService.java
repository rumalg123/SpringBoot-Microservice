package com.rumal.customer_service.service;



import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;

import java.util.UUID;

public interface CustomerService {
    CustomerResponse getByEmail(String email);
    CustomerResponse getByKeycloakId(String keycloakId);

    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse register(RegisterCustomerRequest request);
    CustomerResponse registerIdentity(String keycloakId, String email, RegisterIdentityCustomerRequest request);
    CustomerResponse getById(UUID id);
}
