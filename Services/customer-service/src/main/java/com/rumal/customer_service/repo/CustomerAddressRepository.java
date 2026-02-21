package com.rumal.customer_service.repo;

import com.rumal.customer_service.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByCustomerIdAndDeletedFalseOrderByUpdatedAtDesc(UUID customerId);
    Optional<CustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);
    Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);
    Optional<CustomerAddress> findFirstByCustomerIdAndDeletedFalseOrderByUpdatedAtDesc(UUID customerId);
}
