package com.rumal.customer_service.controller;

import com.rumal.customer_service.dto.CustomerResponse;
import com.rumal.customer_service.dto.InternalCustomerSummary;
import com.rumal.customer_service.security.InternalRequestVerifier;
import com.rumal.customer_service.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerService customerService;

    @GetMapping("/keycloak/{keycloakId}")
    public InternalCustomerSummary getByKeycloakId(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable String keycloakId
    ) {
        internalRequestVerifier.verify(internalAuth);
        CustomerResponse customer = customerService.getByKeycloakId(keycloakId);
        return InternalCustomerSummary.fromFullName(customer.id(), customer.name());
    }
}
