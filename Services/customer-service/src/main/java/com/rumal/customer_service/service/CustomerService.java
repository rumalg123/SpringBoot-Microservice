package com.rumal.customer_service.service;



import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerAddressRequest;
import com.rumal.customer_service.dto.CustomerAddressResponse;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.dto.UpdateCustomerProfileRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {
    CustomerResponse getByEmail(String email);
    CustomerResponse getByKeycloakId(String keycloakId);

    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse register(RegisterCustomerRequest request);
    CustomerResponse registerIdentity(String keycloakId, String email, RegisterIdentityCustomerRequest request);
    CustomerResponse updateProfile(String keycloakId, UpdateCustomerProfileRequest request);
    CustomerResponse getById(UUID id);

    List<CustomerAddressResponse> listAddressesByKeycloak(String keycloakId);
    CustomerAddressResponse addAddressByKeycloak(String keycloakId, CustomerAddressRequest request);
    CustomerAddressResponse updateAddressByKeycloak(String keycloakId, UUID addressId, CustomerAddressRequest request);
    void softDeleteAddressByKeycloak(String keycloakId, UUID addressId);
    CustomerAddressResponse setDefaultShippingByKeycloak(String keycloakId, UUID addressId);
    CustomerAddressResponse setDefaultBillingByKeycloak(String keycloakId, UUID addressId);

    CustomerAddressResponse getAddressByCustomerId(UUID customerId, UUID addressId);
}
