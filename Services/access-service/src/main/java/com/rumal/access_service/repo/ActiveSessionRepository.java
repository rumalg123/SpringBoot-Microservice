package com.rumal.access_service.repo;

import com.rumal.access_service.entity.ActiveSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, UUID> {
    List<ActiveSession> findByKeycloakIdOrderByLastActivityAtDesc(String keycloakId);
    Page<ActiveSession> findByKeycloakIdOrderByLastActivityAtDesc(String keycloakId, Pageable pageable);
    void deleteByKeycloakId(String keycloakId);
}
