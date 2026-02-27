package com.rumal.admin_service.service;

import com.rumal.admin_service.dto.AdminAuditLogResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.entity.AdminAuditLog;
import com.rumal.admin_service.repo.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    @Transactional
    public void log(String actorKeycloakId, String actorRoles, String action,
                    String resourceType, String resourceId, String details, String ipAddress) {
        AdminAuditLog entry = AdminAuditLog.builder()
                .actorKeycloakId(actorKeycloakId)
                .actorRoles(actorRoles)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details != null && details.length() > 2000 ? details.substring(0, 2000) : details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
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
                log.getId(), log.getActorKeycloakId(), log.getActorRoles(),
                log.getAction(), log.getResourceType(), log.getResourceId(),
                log.getDetails(), log.getIpAddress(), log.getCreatedAt()
        );
    }
}
