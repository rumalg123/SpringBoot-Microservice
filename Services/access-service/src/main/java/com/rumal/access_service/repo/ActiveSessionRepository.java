package com.rumal.access_service.repo;

import com.rumal.access_service.entity.ActiveSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, UUID> {
    List<ActiveSession> findByKeycloakIdIgnoreCaseOrderByLastActivityAtDesc(String keycloakId);
    Page<ActiveSession> findByKeycloakIdIgnoreCaseOrderByLastActivityAtDesc(String keycloakId, Pageable pageable);
    void deleteByKeycloakIdIgnoreCase(String keycloakId);
    Optional<ActiveSession> findByKeycloakSessionIdIgnoreCase(String keycloakSessionId);
    boolean existsByKeycloakSessionIdIgnoreCase(String keycloakSessionId);
    void deleteByKeycloakSessionIdIgnoreCase(String keycloakSessionId);
    Optional<ActiveSession> findByIdAndKeycloakIdIgnoreCase(UUID id, String keycloakId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ActiveSession session
               set session.keycloakId = :keycloakId,
                   session.ipAddress = :ipAddress,
                   session.userAgent = :userAgent,
                   session.lastActivityAt = :lastActivityAt
             where lower(session.keycloakSessionId) = lower(:keycloakSessionId)
            """)
    int touchByKeycloakSessionId(
            @Param("keycloakId") String keycloakId,
            @Param("keycloakSessionId") String keycloakSessionId,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("lastActivityAt") Instant lastActivityAt
    );
}
