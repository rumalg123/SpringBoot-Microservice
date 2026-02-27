package com.rumal.admin_service.repo;

import com.rumal.admin_service.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID>, JpaSpecificationExecutor<AdminAuditLog> {

    Page<AdminAuditLog> findByActorKeycloakId(String actorKeycloakId, Pageable pageable);

    Page<AdminAuditLog> findByAction(String action, Pageable pageable);

    Page<AdminAuditLog> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    Page<AdminAuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);
}
