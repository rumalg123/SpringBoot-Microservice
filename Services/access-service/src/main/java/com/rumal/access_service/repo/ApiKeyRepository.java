package com.rumal.access_service.repo;

import com.rumal.access_service.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByKeycloakIdAndActiveTrueOrderByCreatedAtDesc(String keycloakId);
    List<ApiKey> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);
    Page<ApiKey> findByKeycloakId(String keycloakId, Pageable pageable);
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
    List<ApiKey> findByActiveTrueAndExpiresAtBefore(Instant now);
    Page<ApiKey> findByActiveTrueAndExpiresAtBefore(Instant now, Pageable pageable);
}
