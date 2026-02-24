package com.rumal.customer_service.repo;

import com.rumal.customer_service.entity.CommunicationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunicationPreferencesRepository extends JpaRepository<CommunicationPreferences, UUID> {
    Optional<CommunicationPreferences> findByCustomerId(UUID customerId);
}
