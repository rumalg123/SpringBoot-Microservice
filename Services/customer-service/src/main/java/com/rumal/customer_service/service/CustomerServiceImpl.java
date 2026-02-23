package com.rumal.customer_service.service;

import com.rumal.customer_service.auth.KeycloakManagementService;
import com.rumal.customer_service.auth.KeycloakUserExistsException;
import com.rumal.customer_service.dto.CreateCustomerRequest;
import com.rumal.customer_service.dto.CustomerAddressRequest;
import com.rumal.customer_service.dto.CustomerAddressResponse;
import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.RegisterIdentityCustomerRequest;
import com.rumal.customer_service.dto.RegisterCustomerRequest;
import com.rumal.customer_service.dto.UpdateCustomerProfileRequest;
import com.rumal.customer_service.entity.Customer;
import com.rumal.customer_service.entity.CustomerAddress;
import com.rumal.customer_service.exception.DuplicateResourceException;
import com.rumal.customer_service.exception.ResourceNotFoundException;
import com.rumal.customer_service.repo.CustomerAddressRepository;
import com.rumal.customer_service.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final KeycloakManagementService keycloakManagementService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public CustomerResponse getByEmail(String email) {
        String normalized = email.trim().toLowerCase();

        Customer c = customerRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with email: " + normalized));

        return toResponse(c);
    }

    @Override
    @Cacheable(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    public CustomerResponse getByKeycloakId(String keycloakId) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

        Customer c = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));

        return toResponse(c);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        final String resolvedKeycloakId = keycloakId;

        return transactionTemplate.execute(status -> {
            Customer saved = customerRepository.save(
                    Customer.builder()
                            .name(request.name().trim())
                            .email(email)
                            .keycloakId(resolvedKeycloakId)
                            .build()
            );
            return toResponse(saved);
        });
    }

    @Override
    @CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CustomerResponse registerIdentity(String keycloakId, String email, RegisterIdentityCustomerRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
        String resolvedEmail = email;
        String resolvedName = request != null ? request.name() : null;

        if (resolvedEmail == null || resolvedEmail.isBlank() || resolvedName == null || resolvedName.isBlank()) {
            var user = keycloakManagementService.getUserById(normalizedKeycloakId);
            if (resolvedEmail == null || resolvedEmail.isBlank()) {
                resolvedEmail = user.email();
            }
            if (resolvedName == null || resolvedName.isBlank()) {
                resolvedName = user.name();
            }
        }

        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new ResourceNotFoundException("Customer email is required");
        }

        String normalizedEmail = resolvedEmail.trim().toLowerCase();

        Customer existingByKeycloak = customerRepository.findByKeycloakId(normalizedKeycloakId).orElse(null);
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
                        .keycloakId(normalizedKeycloakId)
                        .build()
        );

        return toResponse(saved);
    }

    @Override
    @CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerResponse updateProfile(String keycloakId, UpdateCustomerProfileRequest request) {
        String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

        Customer customer = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));

        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        String customerDbKeycloakId = customer.getKeycloakId();

        if (StringUtils.hasText(customerDbKeycloakId)) {
            keycloakManagementService.updateUserNames(customerDbKeycloakId, firstName, lastName);
        }

        return transactionTemplate.execute(status -> {
            Customer managed = customerRepository.findByKeycloakId(normalizedKeycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
            managed.setName(fullName);
            Customer saved = customerRepository.save(managed);
            return toResponse(saved);
        });
    }

    @Override
    public CustomerResponse getById(UUID id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        return toResponse(c);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public List<CustomerAddressResponse> listAddressesByKeycloak(String keycloakId) {
        Customer customer = findCustomerByKeycloakId(keycloakId);
        return customerAddressRepository.findByCustomerIdAndDeletedFalseOrderByUpdatedAtDesc(customer.getId()).stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CustomerAddressResponse addAddressByKeycloak(String keycloakId, CustomerAddressRequest request) {
        Customer customer = findCustomerByKeycloakId(keycloakId);

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .build();
        applyAddressFields(address, request);
        address.setDefaultShipping(Boolean.TRUE.equals(request.defaultShipping()));
        address.setDefaultBilling(Boolean.TRUE.equals(request.defaultBilling()));

        CustomerAddress saved = customerAddressRepository.save(address);
        rebalanceDefaults(
                customer.getId(),
                Boolean.TRUE.equals(request.defaultShipping()) ? saved.getId() : null,
                Boolean.TRUE.equals(request.defaultBilling()) ? saved.getId() : null
        );

        return toAddressResponse(findActiveAddress(customer.getId(), saved.getId()));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CustomerAddressResponse updateAddressByKeycloak(String keycloakId, UUID addressId, CustomerAddressRequest request) {
        Customer customer = findCustomerByKeycloakId(keycloakId);
        CustomerAddress address = findActiveAddress(customer.getId(), addressId);

        applyAddressFields(address, request);
        if (Boolean.TRUE.equals(request.defaultShipping())) {
            address.setDefaultShipping(true);
        }
        if (Boolean.TRUE.equals(request.defaultBilling())) {
            address.setDefaultBilling(true);
        }

        customerAddressRepository.save(address);
        rebalanceDefaults(
                customer.getId(),
                Boolean.TRUE.equals(request.defaultShipping()) ? address.getId() : null,
                Boolean.TRUE.equals(request.defaultBilling()) ? address.getId() : null
        );

        return toAddressResponse(findActiveAddress(customer.getId(), address.getId()));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeleteAddressByKeycloak(String keycloakId, UUID addressId) {
        Customer customer = findCustomerByKeycloakId(keycloakId);
        CustomerAddress address = findActiveAddress(customer.getId(), addressId);
        address.setDeleted(true);
        address.setDefaultShipping(false);
        address.setDefaultBilling(false);
        customerAddressRepository.save(address);
        rebalanceDefaults(customer.getId(), null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CustomerAddressResponse setDefaultShippingByKeycloak(String keycloakId, UUID addressId) {
        Customer customer = findCustomerByKeycloakId(keycloakId);
        findActiveAddress(customer.getId(), addressId);
        rebalanceDefaults(customer.getId(), addressId, null);
        return toAddressResponse(findActiveAddress(customer.getId(), addressId));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CustomerAddressResponse setDefaultBillingByKeycloak(String keycloakId, UUID addressId) {
        Customer customer = findCustomerByKeycloakId(keycloakId);
        findActiveAddress(customer.getId(), addressId);
        rebalanceDefaults(customer.getId(), null, addressId);
        return toAddressResponse(findActiveAddress(customer.getId(), addressId));
    }

    @Override
    public CustomerAddressResponse getAddressByCustomerId(UUID customerId, UUID addressId) {
        return toAddressResponse(findActiveAddress(customerId, addressId));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getCreatedAt());
    }

    private CustomerAddressResponse toAddressResponse(CustomerAddress address) {
        return new CustomerAddressResponse(
                address.getId(),
                address.getCustomer().getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.isDefaultShipping(),
                address.isDefaultBilling(),
                address.isDeleted(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    private Customer findCustomerByKeycloakId(String keycloakId) {
        return customerRepository.findByKeycloakId(normalizeKeycloakId(keycloakId))
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
    }

    private String normalizeKeycloakId(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            throw new ResourceNotFoundException("Customer not found for keycloak id");
        }
        return keycloakId.trim();
    }

    private CustomerAddress findActiveAddress(UUID customerId, UUID addressId) {
        return customerAddressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
    }

    private void applyAddressFields(CustomerAddress address, CustomerAddressRequest request) {
        address.setLabel(normalizeNullable(request.label(), 50));
        address.setRecipientName(normalizeRequired(request.recipientName()));
        address.setPhone(normalizePhone(request.phone()));
        address.setLine1(normalizeRequired(request.line1()));
        address.setLine2(normalizeNullable(request.line2(), 180));
        address.setCity(normalizeRequired(request.city()));
        address.setState(normalizeRequired(request.state()));
        address.setPostalCode(normalizeRequired(request.postalCode()));
        address.setCountryCode(normalizeRequired(request.countryCode()).toUpperCase());
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        String normalized = normalizeRequired(value).replaceAll("\\s+", " ");
        if (normalized.length() > 32) {
            return normalized.substring(0, 32);
        }
        return normalized;
    }

    private void rebalanceDefaults(UUID customerId, UUID preferredShippingId, UUID preferredBillingId) {
        List<CustomerAddress> active = customerAddressRepository.findByCustomerIdAndDeletedFalseOrderByUpdatedAtDesc(customerId);
        if (active.isEmpty()) {
            return;
        }

        UUID shippingTarget = resolveTargetId(active, preferredShippingId, true);
        UUID billingTarget = resolveTargetId(active, preferredBillingId, false);

        boolean changed = false;
        for (CustomerAddress address : active) {
            boolean shouldShipping = address.getId().equals(shippingTarget);
            boolean shouldBilling = address.getId().equals(billingTarget);
            if (address.isDefaultShipping() != shouldShipping) {
                address.setDefaultShipping(shouldShipping);
                changed = true;
            }
            if (address.isDefaultBilling() != shouldBilling) {
                address.setDefaultBilling(shouldBilling);
                changed = true;
            }
        }
        if (changed) {
            customerAddressRepository.saveAll(active);
        }
    }

    private UUID resolveTargetId(List<CustomerAddress> active, UUID preferredId, boolean shipping) {
        if (preferredId != null && active.stream().anyMatch(address -> address.getId().equals(preferredId))) {
            return preferredId;
        }

        for (CustomerAddress address : active) {
            boolean currentDefault = shipping ? address.isDefaultShipping() : address.isDefaultBilling();
            if (currentDefault) {
                return address.getId();
            }
        }

        return active.getFirst().getId();
    }
}
