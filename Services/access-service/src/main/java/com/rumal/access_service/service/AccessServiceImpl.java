package com.rumal.access_service.service;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.dto.AccessChangeAuditResponse;
import com.rumal.access_service.dto.ActiveSessionResponse;
import com.rumal.access_service.dto.ApiKeyResponse;
import com.rumal.access_service.dto.CreateApiKeyRequest;
import com.rumal.access_service.dto.CreateApiKeyResponse;
import com.rumal.access_service.dto.PermissionGroupResponse;
import com.rumal.access_service.dto.PlatformAccessLookupResponse;
import com.rumal.access_service.dto.PlatformStaffAccessResponse;
import com.rumal.access_service.dto.RegisterSessionRequest;
import com.rumal.access_service.dto.UpsertPermissionGroupRequest;
import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.access_service.dto.VendorStaffAccessResponse;
import com.rumal.access_service.entity.AccessChangeAction;
import com.rumal.access_service.entity.AccessChangeAudit;
import com.rumal.access_service.entity.ActiveSession;
import com.rumal.access_service.entity.ApiKey;
import com.rumal.access_service.entity.PermissionGroup;
import com.rumal.access_service.entity.PermissionGroupScope;
import com.rumal.access_service.entity.PlatformPermission;
import com.rumal.access_service.entity.PlatformStaffAccess;
import com.rumal.access_service.entity.VendorPermission;
import com.rumal.access_service.entity.VendorStaffAccess;
import com.rumal.access_service.exception.ResourceNotFoundException;
import com.rumal.access_service.exception.ValidationException;
import com.rumal.access_service.repo.ActiveSessionRepository;
import com.rumal.access_service.repo.ApiKeyRepository;
import com.rumal.access_service.repo.PermissionGroupRepository;
import com.rumal.access_service.repo.PlatformStaffAccessRepository;
import com.rumal.access_service.repo.VendorStaffAccessRepository;
import com.rumal.access_service.repo.AccessChangeAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class AccessServiceImpl implements AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;
    private final AccessChangeAuditRepository accessChangeAuditRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final ActiveSessionRepository activeSessionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final CacheManager cacheManager;

    @Override
    public AccessChangeAuditPageResponse listAccessAudit(
            String targetType,
            UUID targetId,
            UUID vendorId,
            String action,
            String actorQuery,
            String from,
            String to,
            Integer page,
            Integer size,
            Integer limit
    ) {
        int resolvedPage = normalizeAuditPage(page);
        int resolvedSize = normalizeAuditSize(size, limit);
        PageRequest pageRequest = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedTargetType = trimToNull(targetType);
        if (targetId != null && normalizedTargetType == null) {
            throw new ValidationException("targetType is required when targetId is provided");
        }
        AccessChangeAction normalizedAction = parseAuditAction(action);
        Instant fromInstant = parseOptionalInstant(from, "from");
        Instant toInstant = parseOptionalInstant(to, "to");
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new ValidationException("from must be before or equal to to");
        }
        String normalizedActorQuery = trimToNull(actorQuery);

        Specification<AccessChangeAudit> spec = (root, query, cb) -> cb.conjunction();
        if (normalizedTargetType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("targetType")), normalizedTargetType.toLowerCase(Locale.ROOT)));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (vendorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("vendorId"), vendorId));
        }
        if (normalizedAction != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), normalizedAction));
        }
        if (normalizedActorQuery != null) {
            String like = "%" + normalizedActorQuery.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("actorSub"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("actorRoles"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("actorType"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("changeSource"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("keycloakUserId"), "")), like)
            ));
        }
        if (fromInstant != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
        }
        if (toInstant != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toInstant));
        }

        Page<AccessChangeAudit> auditPage = accessChangeAuditRepository.findAll(spec, pageRequest);
        List<AccessChangeAuditResponse> items = auditPage.getContent().stream().map(this::toAccessAuditResponse).toList();
        return new AccessChangeAuditPageResponse(
                items,
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages()
        );
    }

    @Override
    public List<PlatformStaffAccessResponse> listPlatformStaff() {
        return platformStaffAccessRepository.findByDeletedFalseOrderByEmailAsc().stream().map(this::toPlatformStaffResponse).toList();
    }

    @Override
    public Page<PlatformStaffAccessResponse> listPlatformStaff(Pageable pageable) {
        return platformStaffAccessRepository.findByDeletedFalse(pageable).map(this::toPlatformStaffResponse);
    }

    @Override
    public List<PlatformStaffAccessResponse> listDeletedPlatformStaff() {
        return platformStaffAccessRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toPlatformStaffResponse).toList();
    }

    @Override
    public Page<PlatformStaffAccessResponse> listDeletedPlatformStaff(Pageable pageable) {
        return platformStaffAccessRepository.findByDeletedTrue(pageable).map(this::toPlatformStaffResponse);
    }

    @Override
    public PlatformStaffAccessResponse getPlatformStaffById(UUID id) {
        return platformStaffAccessRepository.findById(id)
                .map(this::toPlatformStaffResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Platform staff not found: " + id));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse createPlatformStaff(UpsertPlatformStaffAccessRequest request) {
        return createPlatformStaff(request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse createPlatformStaff(UpsertPlatformStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = new PlatformStaffAccess();
        applyPlatformStaff(entity, request);
        PlatformStaffAccess saved;
        try {
            saved = platformStaffAccessRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException("Platform staff already exists for this keycloakUserId (concurrent insert detected)");
        }
        recordPlatformAudit(saved, AccessChangeAction.CREATED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictPlatformAccessLookup(keycloakUserId);
            }
        });
        return toPlatformStaffResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse updatePlatformStaff(UUID id, UpsertPlatformStaffAccessRequest request) {
        return updatePlatformStaff(id, request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse updatePlatformStaff(UUID id, UpsertPlatformStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Platform staff not found: " + id));
        String previousKeycloakUserId = entity.getKeycloakUserId();
        applyPlatformStaff(entity, request);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(saved, AccessChangeAction.UPDATED, actorSub, actorRoles, reason);
        String newKeycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictPlatformAccessLookup(previousKeycloakUserId);
                evictPlatformAccessLookup(newKeycloakUserId);
            }
        });
        return toPlatformStaffResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeletePlatformStaff(UUID id) {
        softDeletePlatformStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeletePlatformStaff(UUID id, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Platform staff not found: " + id));
        entity.setDeleted(true);
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(saved, AccessChangeAction.SOFT_DELETED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictPlatformAccessLookup(keycloakUserId);
            }
        });
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse restorePlatformStaff(UUID id) {
        return restorePlatformStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse restorePlatformStaff(UUID id, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Platform staff not found: " + id));
        if (!entity.isDeleted()) {
            throw new ValidationException("Platform staff is not soft deleted: " + id);
        }
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setActive(true);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(saved, AccessChangeAction.RESTORED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictPlatformAccessLookup(keycloakUserId);
            }
        });
        return toPlatformStaffResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "platformAccessLookup", key = "#keycloakUserId == null ? '' : #keycloakUserId.trim().toLowerCase()")
    public PlatformAccessLookupResponse getPlatformAccessByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return platformStaffAccessRepository.findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalse(normalized)
                .map(entity -> {
                    boolean effectiveActive = entity.isActive() && !isExpired(entity.getAccessExpiresAt());
                    return new PlatformAccessLookupResponse(
                            entity.getKeycloakUserId(),
                            effectiveActive,
                            effectiveActive ? entity.getPermissions().stream().map(PlatformPermission::code).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)) : Set.of(),
                            entity.getAccessExpiresAt(),
                            entity.isMfaRequired(),
                            entity.getAllowedIps()
                    );
                })
                .orElseGet(() -> new PlatformAccessLookupResponse(normalized, false, Set.of(), null, false, null));
    }

    @Override
    public List<VendorStaffAccessResponse> listVendorStaff(UUID vendorId) {
        if (vendorId == null) {
            return listAllVendorStaff();
        }
        return vendorStaffAccessRepository.findByVendorIdAndDeletedFalseOrderByEmailAsc(vendorId).stream().map(this::toVendorStaffResponse).toList();
    }

    @Override
    public Page<VendorStaffAccessResponse> listVendorStaff(UUID vendorId, Pageable pageable) {
        if (vendorId == null) {
            return listAllVendorStaff(pageable);
        }
        return vendorStaffAccessRepository.findByVendorIdAndDeletedFalse(vendorId, pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public List<VendorStaffAccessResponse> listAllVendorStaff() {
        return vendorStaffAccessRepository.findByDeletedFalseOrderByVendorIdAscEmailAsc().stream().map(this::toVendorStaffResponse).toList();
    }

    @Override
    public Page<VendorStaffAccessResponse> listAllVendorStaff(Pageable pageable) {
        return vendorStaffAccessRepository.findByDeletedFalse(pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public List<VendorStaffAccessResponse> listDeletedVendorStaff() {
        return vendorStaffAccessRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toVendorStaffResponse).toList();
    }

    @Override
    public Page<VendorStaffAccessResponse> listDeletedVendorStaff(Pageable pageable) {
        return vendorStaffAccessRepository.findByDeletedTrue(pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public VendorStaffAccessResponse getVendorStaffById(UUID id) {
        return vendorStaffAccessRepository.findById(id)
                .map(this::toVendorStaffResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor staff not found: " + id));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request) {
        return createVendorStaff(request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        VendorStaffAccess entity = new VendorStaffAccess();
        applyVendorStaff(entity, request);
        VendorStaffAccess saved;
        try {
            saved = vendorStaffAccessRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException("Vendor staff already exists for this vendor and keycloakUserId (concurrent insert detected)");
        }
        recordVendorAudit(saved, AccessChangeAction.CREATED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictVendorAccessLookup(keycloakUserId);
            }
        });
        return toVendorStaffResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request) {
        return updateVendorStaff(id, request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor staff not found: " + id));
        String previousKeycloakUserId = entity.getKeycloakUserId();
        applyVendorStaff(entity, request);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(saved, AccessChangeAction.UPDATED, actorSub, actorRoles, reason);
        String newKeycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictVendorAccessLookup(previousKeycloakUserId);
                evictVendorAccessLookup(newKeycloakUserId);
            }
        });
        return toVendorStaffResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeleteVendorStaff(UUID id) {
        softDeleteVendorStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor staff not found: " + id));
        entity.setDeleted(true);
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(saved, AccessChangeAction.SOFT_DELETED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictVendorAccessLookup(keycloakUserId);
            }
        });
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse restoreVendorStaff(UUID id) {
        return restoreVendorStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor staff not found: " + id));
        if (!entity.isDeleted()) {
            throw new ValidationException("Vendor staff is not soft deleted: " + id);
        }
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setActive(true);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(saved, AccessChangeAction.RESTORED, actorSub, actorRoles, reason);
        String keycloakUserId = saved.getKeycloakUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictVendorAccessLookup(keycloakUserId);
            }
        });
        return toVendorStaffResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "vendorAccessLookup", key = "#keycloakUserId == null ? '' : #keycloakUserId.trim().toLowerCase()")
    public List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return vendorStaffAccessRepository.findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalseOrderByVendorIdAsc(normalized)
                .stream()
                .map(entity -> {
                    boolean effectiveActive = entity.isActive() && !isExpired(entity.getAccessExpiresAt());
                    return new VendorStaffAccessLookupResponse(
                            entity.getVendorId(),
                            entity.getKeycloakUserId(),
                            effectiveActive,
                            effectiveActive ? entity.getPermissions().stream().map(VendorPermission::code).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)) : Set.of(),
                            entity.isMfaRequired(),
                            entity.getAccessExpiresAt(),
                            entity.getAllowedIps()
                    );
                })
                .toList();
    }

    private void applyPlatformStaff(PlatformStaffAccess entity, UpsertPlatformStaffAccessRequest request) {
        String keycloakUserId = normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120);
        if (entity.getId() == null) {
            if (platformStaffAccessRepository.existsByKeycloakUserIdIgnoreCase(keycloakUserId)) {
                throw new ValidationException("Platform staff already exists for keycloakUserId");
            }
        } else if (platformStaffAccessRepository.existsByKeycloakUserIdIgnoreCaseAndIdNot(keycloakUserId, entity.getId())) {
            throw new ValidationException("Another platform staff row already exists for keycloakUserId");
        }
        entity.setKeycloakUserId(keycloakUserId);
        entity.setEmail(normalizeEmail(request.email(), "email"));
        entity.setDisplayName(trimToNull(request.displayName()));
        entity.setPermissions(normalizePlatformPermissions(request.permissions()));
        entity.setActive(request.active() == null || request.active());
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setPermissionGroupId(request.permissionGroupId());
        entity.setAccessExpiresAt(request.accessExpiresAt());
        entity.setMfaRequired(request.mfaRequired() != null && request.mfaRequired());
        entity.setAllowedIps(trimToNull(request.allowedIps()));
    }

    private void applyVendorStaff(VendorStaffAccess entity, UpsertVendorStaffAccessRequest request) {
        UUID vendorId = request.vendorId();
        if (vendorId == null) {
            throw new ValidationException("vendorId is required");
        }
        String keycloakUserId = normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120);
        if (entity.getId() == null) {
            if (vendorStaffAccessRepository.existsByVendorIdAndKeycloakUserIdIgnoreCase(vendorId, keycloakUserId)) {
                throw new ValidationException("Vendor staff already exists for vendor and keycloakUserId");
            }
        } else if (vendorStaffAccessRepository.existsByVendorIdAndKeycloakUserIdIgnoreCaseAndIdNot(vendorId, keycloakUserId, entity.getId())) {
            throw new ValidationException("Another vendor staff row already exists for vendor and keycloakUserId");
        }
        entity.setVendorId(vendorId);
        entity.setKeycloakUserId(keycloakUserId);
        entity.setEmail(normalizeEmail(request.email(), "email"));
        entity.setDisplayName(trimToNull(request.displayName()));
        entity.setPermissions(normalizeVendorPermissions(request.permissions()));
        entity.setActive(request.active() == null || request.active());
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setMfaRequired(request.mfaRequired() != null && request.mfaRequired());
        entity.setPermissionGroupId(request.permissionGroupId());
        entity.setAccessExpiresAt(request.accessExpiresAt());
        entity.setAllowedIps(trimToNull(request.allowedIps()));
    }

    private Set<PlatformPermission> normalizePlatformPermissions(Set<PlatformPermission> permissions) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<VendorPermission> normalizeVendorPermissions(Set<VendorPermission> permissions) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRequired(String value, String fieldName, int maxLen) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new ValidationException(fieldName + " is required");
        }
        if (trimmed.length() > maxLen) {
            throw new ValidationException(fieldName + " exceeds max length " + maxLen);
        }
        return trimmed;
    }

    private String normalizeEmail(String value, String fieldName) {
        return normalizeRequired(value, fieldName, 180).toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private PlatformStaffAccessResponse toPlatformStaffResponse(PlatformStaffAccess entity) {
        return new PlatformStaffAccessResponse(
                entity.getId(),
                entity.getKeycloakUserId(),
                entity.getEmail(),
                entity.getDisplayName(),
                Set.copyOf(entity.getPermissions()),
                entity.getPermissionGroupId(),
                entity.getAccessExpiresAt(),
                entity.isMfaRequired(),
                entity.getAllowedIps(),
                entity.isActive(),
                entity.isDeleted(),
                entity.getDeletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private VendorStaffAccessResponse toVendorStaffResponse(VendorStaffAccess entity) {
        return new VendorStaffAccessResponse(
                entity.getId(),
                entity.getVendorId(),
                entity.getKeycloakUserId(),
                entity.getEmail(),
                entity.getDisplayName(),
                Set.copyOf(entity.getPermissions()),
                entity.getPermissionGroupId(),
                entity.isMfaRequired(),
                entity.getAccessExpiresAt(),
                entity.getAllowedIps(),
                entity.isActive(),
                entity.isDeleted(),
                entity.getDeletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void recordPlatformAudit(PlatformStaffAccess entity, AccessChangeAction action, String actorSub, String actorRoles, String reason) {
        if (entity == null || action == null) {
            return;
        }
        accessChangeAuditRepository.save(AccessChangeAudit.builder()
                .targetType("PLATFORM_STAFF")
                .targetId(entity.getId())
                .vendorId(null)
                .keycloakUserId(trimToNull(entity.getKeycloakUserId()))
                .email(trimToNull(entity.getEmail()))
                .action(action)
                .activeAfter(entity.isActive())
                .deletedAfter(entity.isDeleted())
                .permissionsSnapshot(joinPlatformPermissions(entity.getPermissions()))
                .actorSub(trimToNull(actorSub))
                .actorRoles(trimToNull(actorRoles))
                .actorType(StringUtils.hasText(actorSub) ? "USER" : "SYSTEM")
                .changeSource("ADMIN_API")
                .reason(trimToNull(reason))
                .build());
    }

    private void recordVendorAudit(VendorStaffAccess entity, AccessChangeAction action, String actorSub, String actorRoles, String reason) {
        if (entity == null || action == null) {
            return;
        }
        accessChangeAuditRepository.save(AccessChangeAudit.builder()
                .targetType("VENDOR_STAFF")
                .targetId(entity.getId())
                .vendorId(entity.getVendorId())
                .keycloakUserId(trimToNull(entity.getKeycloakUserId()))
                .email(trimToNull(entity.getEmail()))
                .action(action)
                .activeAfter(entity.isActive())
                .deletedAfter(entity.isDeleted())
                .permissionsSnapshot(joinVendorPermissions(entity.getPermissions()))
                .actorSub(trimToNull(actorSub))
                .actorRoles(trimToNull(actorRoles))
                .actorType(StringUtils.hasText(actorSub) ? "USER" : "SYSTEM")
                .changeSource("ADMIN_API")
                .reason(trimToNull(reason))
                .build());
    }

    private String joinPlatformPermissions(Set<PlatformPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.stream().filter(Objects::nonNull).map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String joinVendorPermissions(Set<VendorPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.stream().filter(Objects::nonNull).map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private int normalizeAuditPage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new ValidationException("page must be >= 0");
        }
        return page;
    }

    private int normalizeAuditSize(Integer size, Integer limit) {
        Integer candidate = size != null ? size : limit;
        if (candidate == null) {
            return 20;
        }
        if (candidate < 1 || candidate > 200) {
            throw new ValidationException("size must be between 1 and 200");
        }
        return candidate;
    }

    private AccessChangeAction parseAuditAction(String action) {
        String normalized = trimToNull(action);
        if (normalized == null) {
            return null;
        }
        try {
            return AccessChangeAction.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid audit action: " + normalized);
        }
    }

    private Instant parseOptionalInstant(String rawValue, String fieldName) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception ex) {
            throw new ValidationException(fieldName + " must be an ISO-8601 timestamp");
        }
    }

    private AccessChangeAuditResponse toAccessAuditResponse(AccessChangeAudit entity) {
        return new AccessChangeAuditResponse(
                entity.getId(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getVendorId(),
                entity.getKeycloakUserId(),
                entity.getEmail(),
                entity.getAction(),
                entity.isActiveAfter(),
                entity.isDeletedAfter(),
                splitPermissions(entity.getPermissionsSnapshot()),
                entity.getActorSub(),
                entity.getActorRoles(),
                entity.getActorType(),
                entity.getChangeSource(),
                entity.getReason(),
                entity.getCreatedAt()
        );
    }

    private List<String> splitPermissions(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return List.of();
        }
        return java.util.Arrays.stream(snapshot.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void evictPlatformAccessLookup(String keycloakUserId) {
        evictCacheKey("platformAccessLookup", keycloakUserId);
    }

    private void evictVendorAccessLookup(String keycloakUserId) {
        evictCacheKey("vendorAccessLookup", keycloakUserId);
    }

    private void evictCacheKey(String cacheName, String keycloakUserId) {
        String key = normalizedLookupKey(keycloakUserId);
        if (!StringUtils.hasText(key)) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private String normalizedLookupKey(String keycloakUserId) {
        String trimmed = trimToNull(keycloakUserId);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean isExpired(Instant accessExpiresAt) {
        return accessExpiresAt != null && Instant.now().isAfter(accessExpiresAt);
    }

    // ── Permission Groups ──────────────────────────────────────────────

    @Override
    public List<PermissionGroupResponse> listPermissionGroups(PermissionGroupScope scope) {
        List<PermissionGroup> groups = scope != null
                ? permissionGroupRepository.findByScopeOrderByNameAsc(scope)
                : permissionGroupRepository.findAllByOrderByNameAsc();
        return groups.stream().map(this::toPermissionGroupResponse).toList();
    }

    @Override
    public Page<PermissionGroupResponse> listPermissionGroups(PermissionGroupScope scope, Pageable pageable) {
        Page<PermissionGroup> groups = scope != null
                ? permissionGroupRepository.findByScope(scope, pageable)
                : permissionGroupRepository.findAll(pageable);
        return groups.map(this::toPermissionGroupResponse);
    }

    @Override
    public PermissionGroupResponse getPermissionGroupById(UUID id) {
        return permissionGroupRepository.findById(id)
                .map(this::toPermissionGroupResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Permission group not found: " + id));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PermissionGroupResponse createPermissionGroup(UpsertPermissionGroupRequest request) {
        String name = normalizeRequired(request.name(), "name", 120);
        if (permissionGroupRepository.existsByNameIgnoreCaseAndScope(name, request.scope())) {
            throw new ValidationException("Permission group with this name and scope already exists");
        }
        PermissionGroup entity = PermissionGroup.builder()
                .name(name)
                .description(trimToNull(request.description()))
                .permissions(joinStringSet(request.permissions()))
                .scope(request.scope())
                .build();
        return toPermissionGroupResponse(permissionGroupRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PermissionGroupResponse updatePermissionGroup(UUID id, UpsertPermissionGroupRequest request) {
        PermissionGroup entity = permissionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission group not found: " + id));
        String name = normalizeRequired(request.name(), "name", 120);
        if (permissionGroupRepository.existsByNameIgnoreCaseAndScopeAndIdNot(name, request.scope(), id)) {
            throw new ValidationException("Another permission group with this name and scope already exists");
        }
        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity.setPermissions(joinStringSet(request.permissions()));
        entity.setScope(request.scope());
        return toPermissionGroupResponse(permissionGroupRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deletePermissionGroup(UUID id) {
        if (!permissionGroupRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission group not found: " + id);
        }
        permissionGroupRepository.deleteById(id);
    }

    private PermissionGroupResponse toPermissionGroupResponse(PermissionGroup entity) {
        return new PermissionGroupResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                splitPermissions(entity.getPermissions()),
                entity.getScope(),
                entity.getCreatedAt()
        );
    }

    private String joinStringSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .sorted()
                .collect(Collectors.joining(","));
    }

    // ── Session Management ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public ActiveSessionResponse registerSession(RegisterSessionRequest request) {
        String keycloakId = normalizeRequired(request.keycloakId(), "keycloakId", 120);
        ActiveSession session = ActiveSession.builder()
                .keycloakId(keycloakId)
                .ipAddress(trimToNull(request.ipAddress()))
                .userAgent(trimToNull(request.userAgent()))
                .lastActivityAt(Instant.now())
                .build();
        return toActiveSessionResponse(activeSessionRepository.save(session));
    }

    @Override
    public List<ActiveSessionResponse> listSessionsByKeycloakId(String keycloakId) {
        String normalized = normalizeRequired(keycloakId, "keycloakId", 120);
        return activeSessionRepository.findByKeycloakIdOrderByLastActivityAtDesc(normalized)
                .stream().map(this::toActiveSessionResponse).toList();
    }

    @Override
    public Page<ActiveSessionResponse> listSessionsByKeycloakId(String keycloakId, Pageable pageable) {
        String normalized = normalizeRequired(keycloakId, "keycloakId", 120);
        return activeSessionRepository.findByKeycloakIdOrderByLastActivityAtDesc(normalized, pageable)
                .map(this::toActiveSessionResponse);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeSession(UUID sessionId) {
        if (!activeSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }
        activeSessionRepository.deleteById(sessionId);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeAllSessions(String keycloakId) {
        String normalized = normalizeRequired(keycloakId, "keycloakId", 120);
        activeSessionRepository.deleteByKeycloakId(normalized);
    }

    private ActiveSessionResponse toActiveSessionResponse(ActiveSession entity) {
        return new ActiveSessionResponse(
                entity.getId(),
                entity.getKeycloakId(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getLastActivityAt(),
                entity.getCreatedAt()
        );
    }

    // ── API Key Management ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        String keycloakId = normalizeRequired(request.keycloakId(), "keycloakId", 120);
        String name = normalizeRequired(request.name(), "name", 120);
        String rawKey = generateRawApiKey();
        String keyHash = hashApiKey(rawKey);

        ApiKey entity = ApiKey.builder()
                .keycloakId(keycloakId)
                .keyHash(keyHash)
                .name(name)
                .scope(request.scope())
                .permissions(joinStringSet(request.permissions()))
                .active(true)
                .expiresAt(request.expiresAt())
                .build();
        ApiKey saved = apiKeyRepository.save(entity);
        return new CreateApiKeyResponse(
                saved.getId(),
                saved.getKeycloakId(),
                saved.getName(),
                saved.getScope(),
                splitPermissions(saved.getPermissions()),
                saved.isActive(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                rawKey
        );
    }

    @Override
    public List<ApiKeyResponse> listApiKeys(String keycloakId) {
        String normalized = normalizeRequired(keycloakId, "keycloakId", 120);
        return apiKeyRepository.findByKeycloakIdOrderByCreatedAtDesc(normalized)
                .stream().map(this::toApiKeyResponse).toList();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deleteApiKey(UUID id) {
        ApiKey entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        entity.setActive(false);
        apiKeyRepository.save(entity);
    }

    private ApiKeyResponse toApiKeyResponse(ApiKey entity) {
        return new ApiKeyResponse(
                entity.getId(),
                entity.getKeycloakId(),
                entity.getName(),
                entity.getScope(),
                splitPermissions(entity.getPermissions()),
                entity.isActive(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    private String generateRawApiKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "ak_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Expiry Processing ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 60)
    public int deactivateExpiredAccess() {
        Instant now = Instant.now();
        int count = 0;
        Pageable batch = PageRequest.of(0, 100);

        List<String> platformUserIds = new ArrayList<>();
        Page<PlatformStaffAccess> platformPage;
        do {
            platformPage = platformStaffAccessRepository
                    .findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(now, batch);
            platformPage.forEach(staff -> {
                staff.setActive(false);
                platformUserIds.add(staff.getKeycloakUserId());
            });
            platformStaffAccessRepository.saveAll(platformPage.getContent());
            count += platformPage.getNumberOfElements();
        } while (platformPage.hasNext());

        List<String> vendorUserIds = new ArrayList<>();
        Page<VendorStaffAccess> vendorPage;
        do {
            vendorPage = vendorStaffAccessRepository
                    .findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(now, batch);
            vendorPage.forEach(staff -> {
                staff.setActive(false);
                vendorUserIds.add(staff.getKeycloakUserId());
            });
            vendorStaffAccessRepository.saveAll(vendorPage.getContent());
            count += vendorPage.getNumberOfElements();
        } while (vendorPage.hasNext());

        Page<ApiKey> keyPage;
        do {
            keyPage = apiKeyRepository.findByActiveTrueAndExpiresAtBefore(now, batch);
            keyPage.forEach(key -> key.setActive(false));
            apiKeyRepository.saveAll(keyPage.getContent());
            count += keyPage.getNumberOfElements();
        } while (keyPage.hasNext());

        if (count > 0) {
            log.info("Deactivated {} expired access records", count);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                platformUserIds.forEach(uid -> evictPlatformAccessLookup(uid));
                vendorUserIds.forEach(uid -> evictVendorAccessLookup(uid));
            }
        });
        return count;
    }
}
