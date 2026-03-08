package com.rumal.admin_service.service;

import com.rumal.admin_service.dto.AdminAuditLogResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.entity.AdminAuditLog;
import com.rumal.admin_service.entity.AdminAuditOutboxEvent;
import com.rumal.admin_service.repo.AdminAuditLogRepository;
import com.rumal.admin_service.repo.AdminAuditOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminAuditLogRepository auditLogRepository;
    private final AdminAuditOutboxRepository adminAuditOutboxRepository;
    private final AdminAuditRequestContextResolver adminAuditRequestContextResolver;
    private final AdminAuditPayloadSanitizer adminAuditPayloadSanitizer;

    @Value("${admin.audit.outbox.retry-base-delay-seconds:15}")
    private long retryBaseDelaySeconds;

    @Transactional
    public void log(String actorKeycloakId, String actorRoles, String action,
                    String resourceType, String resourceId, String details, String ipAddress) {
        AdminAuditRequestContext context = adminAuditRequestContextResolver.resolve(actorKeycloakId, actorRoles, ipAddress);
        adminAuditOutboxRepository.save(AdminAuditOutboxEvent.builder()
                .actorKeycloakId(context.actorKeycloakId())
                .actorTenantId(context.actorTenantId())
                .actorRoles(context.actorRoles())
                .actorType(context.actorType())
                .action(trimToNull(action))
                .resourceType(trimToNull(resourceType))
                .resourceId(trimToNull(resourceId))
                .changeSource(context.changeSource())
                .details(adminAuditPayloadSanitizer.sanitizeDetails(details))
                .changeSet(null)
                .ipAddress(trimToNull(context.ipAddress()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    @Transactional
    public void logMutation(
            String actorKeycloakId,
            String actorRoles,
            String action,
            String resourceType,
            String resourceId,
            String details,
            Object beforeState,
            Object afterState,
            String ipAddress
    ) {
        AdminAuditRequestContext context = adminAuditRequestContextResolver.resolve(actorKeycloakId, actorRoles, ipAddress);
        adminAuditOutboxRepository.save(AdminAuditOutboxEvent.builder()
                .actorKeycloakId(context.actorKeycloakId())
                .actorTenantId(context.actorTenantId())
                .actorRoles(context.actorRoles())
                .actorType(context.actorType())
                .action(trimToNull(action))
                .resourceType(trimToNull(resourceType))
                .resourceId(trimToNull(resourceId))
                .changeSource(context.changeSource())
                .details(adminAuditPayloadSanitizer.sanitizeDetails(details))
                .changeSet(adminAuditPayloadSanitizer.buildChangeSet(beforeState, afterState))
                .ipAddress(trimToNull(context.ipAddress()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOutboxBatch(int batchSize) {
        List<AdminAuditOutboxEvent> events = adminAuditOutboxRepository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );
        for (AdminAuditOutboxEvent event : events) {
            processOutboxEvent(event.getId());
        }
    }

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

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> listAuditLogs(
            String actorKeycloakId, String action, String resourceType, String resourceId,
            Instant from, Instant to, int page, int size
    ) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<AdminAuditLog> spec = Specification.where((Specification<AdminAuditLog>) null);
        if (actorKeycloakId != null && !actorKeycloakId.isBlank()) {
            String trimmedActor = actorKeycloakId.trim();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actorKeycloakId"), trimmedActor));
        }
        if (resourceType != null && resourceId != null) {
            spec = spec.and((root, q, cb) -> cb.and(
                    cb.equal(root.get("resourceType"), resourceType),
                    cb.equal(root.get("resourceId"), resourceId)
            ));
        }
        if (action != null && !action.isBlank()) {
            String trimmedAction = action.trim();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), trimmedAction));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        Page<AdminAuditLog> result = auditLogRepository.findAll(spec, pageable);
        return toPageResponse(result);
    }

    private PageResponse<AdminAuditLogResponse> toPageResponse(Page<AdminAuditLog> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }

    private AdminAuditLogResponse toResponse(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getActorKeycloakId(),
                log.getActorTenantId(),
                log.getActorRoles(),
                log.getActorType(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getChangeSource(),
                log.getDetails(),
                log.getChangeSet(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getRequestId(),
                log.getCreatedAt()
        );
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
