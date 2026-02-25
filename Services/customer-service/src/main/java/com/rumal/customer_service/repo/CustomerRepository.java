package com.rumal.customer_service.repo;


import com.rumal.customer_service.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByEmail(String email);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByKeycloakId(String keycloakId);

    // --- Analytics queries ---

    long countByActiveTrue();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.active = true AND c.createdAt >= :since")
    long countNewCustomersSince(@Param("since") java.time.Instant since);

    @Query("SELECT c.loyaltyTier, COUNT(c) FROM Customer c WHERE c.active = true GROUP BY c.loyaltyTier")
    List<Object[]> countByLoyaltyTier();

    @Query(value = "SELECT TO_CHAR(c.created_at, 'YYYY-MM'), COUNT(*) FROM customers c WHERE c.created_at >= :since GROUP BY TO_CHAR(c.created_at, 'YYYY-MM') ORDER BY TO_CHAR(c.created_at, 'YYYY-MM')", nativeQuery = true)
    List<Object[]> countNewCustomersByMonth(@Param("since") java.time.Instant since);
}
