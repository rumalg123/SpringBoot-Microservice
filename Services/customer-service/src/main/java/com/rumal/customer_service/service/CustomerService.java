package com.rumal.customer_service.service;



import com.rumal.customer_service.dto.CommunicationPreferencesResponse;
import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerActivityLogResponse;
import com.rumal.customer_service.dto.CustomerAddressRequest;
import com.rumal.customer_service.dto.CustomerAddressResponse;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.LinkedAccountsResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.dto.UpdateCommunicationPreferencesRequest;
import com.rumal.customer_service.dto.UpdateCustomerProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomerService {
    CustomerResponse getByEmail(String email);
    CustomerResponse getByKeycloakId(String keycloakId);

    CustomerResponse create(CreateCustomerRequest request);
    CustomerResponse register(RegisterCustomerRequest request);
    CustomerResponse registerIdentity(String keycloakId, String email, RegisterIdentityCustomerRequest request);
    CustomerResponse updateProfile(String keycloakId, UpdateCustomerProfileRequest request, String ipAddress);
    CustomerResponse deactivateAccount(String keycloakId);
    CustomerResponse getById(UUID id);

    List<CustomerAddressResponse> listAddressesByKeycloak(String keycloakId);
    CustomerAddressResponse addAddressByKeycloak(String keycloakId, CustomerAddressRequest request, String ipAddress);
    CustomerAddressResponse updateAddressByKeycloak(String keycloakId, UUID addressId, CustomerAddressRequest request, String ipAddress);
    void softDeleteAddressByKeycloak(String keycloakId, UUID addressId, String ipAddress);
    CustomerAddressResponse setDefaultShippingByKeycloak(String keycloakId, UUID addressId);
    CustomerAddressResponse setDefaultBillingByKeycloak(String keycloakId, UUID addressId);

    CustomerAddressResponse getAddressByCustomerId(UUID customerId, UUID addressId);

    // Loyalty
    CustomerResponse addLoyaltyPoints(UUID customerId, int points);

    // Communication preferences
    CommunicationPreferencesResponse getCommunicationPreferences(String keycloakId);
    CommunicationPreferencesResponse updateCommunicationPreferences(String keycloakId, UpdateCommunicationPreferencesRequest request);

    // Activity log
    Page<CustomerActivityLogResponse> getActivityLog(String keycloakId, Pageable pageable);

    // Social login linking
    LinkedAccountsResponse getLinkedAccounts(String keycloakId);
}
