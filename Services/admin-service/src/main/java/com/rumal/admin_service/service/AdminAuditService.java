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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final String CREATED_AT_FIELD = "createdAt";

    private final AdminAuditLogRepository auditLogRepository;
    private final AdminAuditOutboxRepository adminAuditOutboxRepository;
    private final AdminAuditRequestContextResolver adminAuditRequestContextResolver;
    private final AdminAuditPayloadSanitizer adminAuditPayloadSanitizer;
    private final AdminAuditOutboxEventService adminAuditOutboxEventService;

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
    public void logMutation(MutationLogEntry entry) {
        AdminAuditRequestContext context = adminAuditRequestContextResolver.resolve(
                entry.actorKeycloakId(),
                entry.actorRoles(),
                entry.ipAddress()
        );
        adminAuditOutboxRepository.save(AdminAuditOutboxEvent.builder()
                .actorKeycloakId(context.actorKeycloakId())
                .actorTenantId(context.actorTenantId())
                .actorRoles(context.actorRoles())
                .actorType(context.actorType())
                .action(trimToNull(entry.action()))
                .resourceType(trimToNull(entry.resourceType()))
                .resourceId(trimToNull(entry.resourceId()))
                .changeSource(context.changeSource())
                .details(adminAuditPayloadSanitizer.sanitizeDetails(entry.details()))
                .changeSet(adminAuditPayloadSanitizer.buildChangeSet(entry.beforeState(), entry.afterState()))
                .ipAddress(trimToNull(context.ipAddress()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    public void processOutboxBatch(int batchSize) {
        List<AdminAuditOutboxEvent> events = adminAuditOutboxRepository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );
        for (AdminAuditOutboxEvent event : events) {
            adminAuditOutboxEventService.processOutboxEvent(event.getId());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> listAuditLogs(AuditLogQuery query) {
        int safeSize = Math.min(query.size(), 100);
        Pageable pageable = PageRequest.of(query.page(), safeSize, Sort.by(Sort.Direction.DESC, CREATED_AT_FIELD));

        Specification<AdminAuditLog> spec = (root, unusedQuery, cb) -> cb.conjunction();
        if (StringUtils.hasText(query.actorKeycloakId())) {
            String trimmedActor = query.actorKeycloakId().trim();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actorKeycloakId"), trimmedActor));
        }
        if (query.resourceType() != null && query.resourceId() != null) {
            spec = spec.and((root, q, cb) -> cb.and(
                    cb.equal(root.get("resourceType"), query.resourceType()),
                    cb.equal(root.get("resourceId"), query.resourceId())
            ));
        }
        if (StringUtils.hasText(query.action())) {
            String trimmedAction = query.action().trim();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), trimmedAction));
        }
        if (query.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT_FIELD), query.from()));
        }
        if (query.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get(CREATED_AT_FIELD), query.to()));
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

    public record MutationLogEntry(
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
    }

    public record AuditLogQuery(
            String actorKeycloakId,
            String action,
            String resourceType,
            String resourceId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
    }
}
