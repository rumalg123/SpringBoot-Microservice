package com.rumal.admin_service.service;

import com.rumal.admin_service.entity.AdminAuditLog;
import com.rumal.admin_service.entity.AdminAuditOutboxEvent;
import com.rumal.admin_service.repo.AdminAuditLogRepository;
import com.rumal.admin_service.repo.AdminAuditOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class AdminAuditOutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditOutboxEventService.class);

    private final AdminAuditLogRepository auditLogRepository;
    private final AdminAuditOutboxRepository adminAuditOutboxRepository;

    @Value("${admin.audit.outbox.retry-base-delay-seconds:15}")
    private long retryBaseDelaySeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOutboxEvent(UUID eventId) {
        AdminAuditOutboxEvent event = adminAuditOutboxRepository.findById(eventId).orElse(null);
        if (event == null || event.getProcessedAt() != null) {
            return;
        }

        try {
            if (!auditLogRepository.existsBySourceEventId(event.getId())) {
                auditLogRepository.save(AdminAuditLog.builder()
                        .sourceEventId(event.getId())
                        .actorKeycloakId(defaultActor(event.getActorKeycloakId()))
                        .actorTenantId(trimToNull(event.getActorTenantId()))
                        .actorRoles(trimToNull(event.getActorRoles()))
                        .actorType(defaultActorType(event.getActorType()))
                        .action(defaultValue(event.getAction(), "UNKNOWN"))
                        .resourceType(trimToNull(event.getResourceType()))
                        .resourceId(trimToNull(event.getResourceId()))
                        .changeSource(defaultValue(event.getChangeSource(), "SYSTEM"))
                        .details(trimToNull(event.getDetails()))
                        .changeSet(trimToNull(event.getChangeSet()))
                        .ipAddress(trimToNull(event.getIpAddress()))
                        .userAgent(trimToNull(event.getUserAgent()))
                        .requestId(trimToNull(event.getRequestId()))
                        .build());
            }
            markProcessed(event);
        } catch (DataIntegrityViolationException duplicate) {
            markProcessed(event);
        } catch (Exception ex) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLastError(truncate(ex.getMessage(), 500));
            event.setAvailableAt(Instant.now().plusSeconds(resolveRetryDelaySeconds(event.getAttemptCount())));
            adminAuditOutboxRepository.save(event);
            log.warn("Admin audit outbox event {} failed on attempt {}", event.getId(), event.getAttemptCount(), ex);
        }
    }

    private void markProcessed(AdminAuditOutboxEvent event) {
        event.setProcessedAt(Instant.now());
        event.setLastError(null);
        adminAuditOutboxRepository.save(event);
    }

    private String defaultActor(String actorKeycloakId) {
        return StringUtils.hasText(actorKeycloakId) ? actorKeycloakId.trim() : "system";
    }

    private String defaultActorType(String actorType) {
        return StringUtils.hasText(actorType) ? actorType.trim() : "SYSTEM";
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private long resolveRetryDelaySeconds(int attemptCount) {
        long base = Math.max(5L, retryBaseDelaySeconds);
        long multiplier = Math.max(1L, attemptCount);
        return Math.min(900L, base * multiplier * multiplier);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
