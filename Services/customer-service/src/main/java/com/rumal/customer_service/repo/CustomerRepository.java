package com.rumal.customer_service.repo;


import com.rumal.customer_service.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByEmail(String email);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByAuth0Id(String auth0Id);
}
