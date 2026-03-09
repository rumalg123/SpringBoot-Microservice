package com.rumal.access_service.service;

import com.rumal.access_service.client.GatewaySessionClient;
import com.rumal.access_service.dto.AccessAuditQuery;
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
import com.rumal.access_service.entity.AccessAuditOutboxEvent;
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
import com.rumal.access_service.repo.AccessAuditOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class AccessServiceImpl implements AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ADMIN_API_SOURCE = "ADMIN_API";
    private static final String SYSTEM_VALUE = "SYSTEM";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String EMAIL_FIELD = "email";
    private static final String KEYCLOAK_ID_FIELD = "keycloakId";
    private static final String KEYCLOAK_SESSION_ID_FIELD = "keycloakSessionId";
    private static final String KEYCLOAK_USER_ID_FIELD = "keycloakUserId";
    private static final String PLATFORM_STAFF_NOT_FOUND = "Platform staff not found: ";
    private static final String VENDOR_STAFF_NOT_FOUND = "Vendor staff not found: ";
    private static final String PERMISSION_GROUP_NOT_FOUND = "Permission group not found: ";
    private static final int SESSION_SYNC_MAX_ATTEMPTS = 3;
    private static final long SESSION_ACTIVITY_WRITE_THROTTLE_SECONDS = 30;

    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;
    private final AccessChangeAuditRepository accessChangeAuditRepository;
    private final AccessAuditOutboxRepository accessAuditOutboxRepository;
    private final PermissionGroupRepository permissionGroupRepository;
    private final ActiveSessionRepository activeSessionRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final GatewaySessionClient gatewaySessionClient;
    private final CacheManager cacheManager;
    private final AccessAuditRequestContextResolver accessAuditRequestContextResolver;
    private final AccessAuditPayloadSanitizer accessAuditPayloadSanitizer;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<AccessService> selfProvider;

    @Value("${access.audit.outbox.retry-base-delay-seconds:15}")
    private long accessAuditRetryBaseDelaySeconds;

    public AccessServiceImpl(
            PlatformStaffAccessRepository platformStaffAccessRepository,
            VendorStaffAccessRepository vendorStaffAccessRepository,
            AccessChangeAuditRepository accessChangeAuditRepository,
            AccessAuditOutboxRepository accessAuditOutboxRepository,
            PermissionGroupRepository permissionGroupRepository,
            ActiveSessionRepository activeSessionRepository,
            ApiKeyRepository apiKeyRepository,
            GatewaySessionClient gatewaySessionClient,
            CacheManager cacheManager,
            AccessAuditRequestContextResolver accessAuditRequestContextResolver,
            AccessAuditPayloadSanitizer accessAuditPayloadSanitizer,
            PlatformTransactionManager transactionManager,
            ObjectProvider<AccessService> selfProvider
    ) {
        this.platformStaffAccessRepository = platformStaffAccessRepository;
        this.vendorStaffAccessRepository = vendorStaffAccessRepository;
        this.accessChangeAuditRepository = accessChangeAuditRepository;
        this.accessAuditOutboxRepository = accessAuditOutboxRepository;
        this.permissionGroupRepository = permissionGroupRepository;
        this.activeSessionRepository = activeSessionRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.gatewaySessionClient = gatewaySessionClient;
        this.cacheManager = cacheManager;
        this.accessAuditRequestContextResolver = accessAuditRequestContextResolver;
        this.accessAuditPayloadSanitizer = accessAuditPayloadSanitizer;
        this.transactionManager = transactionManager;
        this.selfProvider = selfProvider;
    }

    @Override
    public AccessChangeAuditPageResponse listAccessAudit(AccessAuditQuery auditQuery) {
        int resolvedPage = normalizeAuditPage(auditQuery.page());
        int resolvedSize = normalizeAuditSize(auditQuery.size(), auditQuery.limit());
        PageRequest pageRequest = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, CREATED_AT_FIELD));
        String normalizedTargetType = trimToNull(auditQuery.targetType());
        UUID targetId = auditQuery.targetId();
        UUID vendorId = auditQuery.vendorId();
        if (targetId != null && normalizedTargetType == null) {
            throw new ValidationException("targetType is required when targetId is provided");
        }
        AccessChangeAction normalizedAction = parseAuditAction(auditQuery.action());
        Instant fromInstant = parseOptionalInstant(auditQuery.from(), "from");
        Instant toInstant = parseOptionalInstant(auditQuery.to(), "to");
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new ValidationException("from must be before or equal to to");
        }
        String normalizedActorQuery = trimToNull(auditQuery.actorQuery());

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
            String like = "%" + escapeLikePattern(normalizedActorQuery.toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("actorSub"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("actorRoles"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("actorType"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("changeSource"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get(EMAIL_FIELD), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get(KEYCLOAK_USER_ID_FIELD), "")), like, '\\')
            ));
        }
        if (fromInstant != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT_FIELD), fromInstant));
        }
        if (toInstant != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get(CREATED_AT_FIELD), toInstant));
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
    public Page<PlatformStaffAccessResponse> listPlatformStaff(Pageable pageable) {
        return platformStaffAccessRepository.findByDeletedFalse(pageable).map(this::toPlatformStaffResponse);
    }

    @Override
    public Page<PlatformStaffAccessResponse> listDeletedPlatformStaff(Pageable pageable) {
        return platformStaffAccessRepository.findByDeletedTrue(pageable).map(this::toPlatformStaffResponse);
    }

    @Override
    public PlatformStaffAccessResponse getPlatformStaffById(UUID id) {
        return platformStaffAccessRepository.findById(id)
                .map(this::toPlatformStaffResponse)
                .orElseThrow(() -> new ResourceNotFoundException(PLATFORM_STAFF_NOT_FOUND + id));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse createPlatformStaff(UpsertPlatformStaffAccessRequest request) {
        return self().createPlatformStaff(request, null, null, null);
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
            throw new ValidationException("Platform staff already exists for this " + KEYCLOAK_USER_ID_FIELD + " (concurrent insert detected)");
        }
        recordPlatformAudit(
                saved,
                null,
                toPlatformStaffResponse(saved),
                new AuditCommand(AccessChangeAction.CREATED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        return self().updatePlatformStaff(id, request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse updatePlatformStaff(UUID id, UpsertPlatformStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(PLATFORM_STAFF_NOT_FOUND + id));
        PlatformStaffAccessResponse beforeState = toPlatformStaffResponse(entity);
        String previousKeycloakUserId = entity.getKeycloakUserId();
        applyPlatformStaff(entity, request);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(
                saved,
                beforeState,
                toPlatformStaffResponse(saved),
                new AuditCommand(AccessChangeAction.UPDATED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        self().softDeletePlatformStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeletePlatformStaff(UUID id, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(PLATFORM_STAFF_NOT_FOUND + id));
        PlatformStaffAccessResponse beforeState = toPlatformStaffResponse(entity);
        entity.setDeleted(true);
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(
                saved,
                beforeState,
                toPlatformStaffResponse(saved),
                new AuditCommand(AccessChangeAction.SOFT_DELETED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        return self().restorePlatformStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PlatformStaffAccessResponse restorePlatformStaff(UUID id, String actorSub, String actorRoles, String reason) {
        PlatformStaffAccess entity = platformStaffAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PLATFORM_STAFF_NOT_FOUND + id));
        if (!entity.isDeleted()) {
            throw new ValidationException("Platform staff is not soft deleted: " + id);
        }
        PlatformStaffAccessResponse beforeState = toPlatformStaffResponse(entity);
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        boolean shouldBeActive = !isExpired(entity.getAccessExpiresAt());
        entity.setActive(shouldBeActive);
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(
                saved,
                beforeState,
                toPlatformStaffResponse(saved),
                new AuditCommand(AccessChangeAction.RESTORED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        String normalized = normalizeRequired(keycloakUserId, KEYCLOAK_USER_ID_FIELD, 120);
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
    public Page<VendorStaffAccessResponse> listVendorStaff(UUID vendorId, Pageable pageable) {
        if (vendorId == null) {
            return listAllVendorStaff(pageable);
        }
        return vendorStaffAccessRepository.findByVendorIdAndDeletedFalse(vendorId, pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public Page<VendorStaffAccessResponse> listAllVendorStaff(Pageable pageable) {
        return vendorStaffAccessRepository.findByDeletedFalse(pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public Page<VendorStaffAccessResponse> listDeletedVendorStaff(Pageable pageable) {
        return vendorStaffAccessRepository.findByDeletedTrue(pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public Page<VendorStaffAccessResponse> listDeletedVendorStaff(UUID vendorId, Pageable pageable) {
        if (vendorId == null) {
            return listDeletedVendorStaff(pageable);
        }
        return vendorStaffAccessRepository.findByVendorIdAndDeletedTrue(vendorId, pageable).map(this::toVendorStaffResponse);
    }

    @Override
    public VendorStaffAccessResponse getVendorStaffById(UUID id) {
        return vendorStaffAccessRepository.findById(id)
                .map(this::toVendorStaffResponse)
                .orElseThrow(() -> new ResourceNotFoundException(VENDOR_STAFF_NOT_FOUND + id));
    }

    @Override
    public VendorStaffAccessResponse getVendorStaffById(UUID id, UUID callerVendorId) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENDOR_STAFF_NOT_FOUND + id));
        verifyVendorTenancy(entity, callerVendorId);
        return toVendorStaffResponse(entity);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request) {
        return self().createVendorStaff(request, null, null, null);
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
            throw new ValidationException("Vendor staff already exists for this vendor and " + KEYCLOAK_USER_ID_FIELD + " (concurrent insert detected)");
        }
        recordVendorAudit(
                saved,
                null,
                toVendorStaffResponse(saved),
                new AuditCommand(AccessChangeAction.CREATED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        return self().updateVendorStaff(id, request, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENDOR_STAFF_NOT_FOUND + id));
        if (!entity.getVendorId().equals(request.vendorId())) {
            throw new ValidationException("Vendor staff does not belong to the specified vendor");
        }
        VendorStaffAccessResponse beforeState = toVendorStaffResponse(entity);
        String previousKeycloakUserId = entity.getKeycloakUserId();
        applyVendorStaff(entity, request);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(
                saved,
                beforeState,
                toVendorStaffResponse(saved),
                new AuditCommand(AccessChangeAction.UPDATED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        self().softDeleteVendorStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason) {
        self().softDeleteVendorStaff(id, actorSub, actorRoles, reason, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse restoreVendorStaff(UUID id) {
        return self().restoreVendorStaff(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason, UUID callerVendorId) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENDOR_STAFF_NOT_FOUND + id));
        verifyVendorTenancy(entity, callerVendorId);
        VendorStaffAccessResponse beforeState = toVendorStaffResponse(entity);
        entity.setDeleted(true);
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(
                saved,
                beforeState,
                toVendorStaffResponse(saved),
                new AuditCommand(AccessChangeAction.SOFT_DELETED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
    public VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason) {
        return self().restoreVendorStaff(id, actorSub, actorRoles, reason, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason, UUID callerVendorId) {
        VendorStaffAccess entity = vendorStaffAccessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(VENDOR_STAFF_NOT_FOUND + id));
        verifyVendorTenancy(entity, callerVendorId);
        if (!entity.isDeleted()) {
            throw new ValidationException("Vendor staff is not soft deleted: " + id);
        }
        VendorStaffAccessResponse beforeState = toVendorStaffResponse(entity);
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        boolean shouldBeActive = !isExpired(entity.getAccessExpiresAt());
        entity.setActive(shouldBeActive);
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(
                saved,
                beforeState,
                toVendorStaffResponse(saved),
                new AuditCommand(AccessChangeAction.RESTORED, actorSub, actorRoles, reason, ADMIN_API_SOURCE)
        );
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
        String normalized = normalizeRequired(keycloakUserId, KEYCLOAK_USER_ID_FIELD, 120);
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
        String keycloakUserId = normalizeRequired(request.keycloakUserId(), KEYCLOAK_USER_ID_FIELD, 120);
        if (entity.getId() == null) {
            if (platformStaffAccessRepository.existsByKeycloakUserIdIgnoreCase(keycloakUserId)) {
                throw new ValidationException("Platform staff already exists for " + KEYCLOAK_USER_ID_FIELD);
            }
        } else if (platformStaffAccessRepository.existsByKeycloakUserIdIgnoreCaseAndIdNot(keycloakUserId, entity.getId())) {
            throw new ValidationException("Another platform staff row already exists for " + KEYCLOAK_USER_ID_FIELD);
        }
        entity.setKeycloakUserId(keycloakUserId);
        entity.setEmail(normalizeEmail(request.email(), EMAIL_FIELD));
        entity.setDisplayName(trimToNull(request.displayName()));
        entity.setPermissions(normalizePlatformPermissions(request.permissions()));
        entity.setActive(request.active() == null || request.active());
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        validatePermissionGroupScope(request.permissionGroupId(), PermissionGroupScope.PLATFORM);
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
        String keycloakUserId = normalizeRequired(request.keycloakUserId(), KEYCLOAK_USER_ID_FIELD, 120);
        if (entity.getId() == null) {
            if (vendorStaffAccessRepository.existsByVendorIdAndKeycloakUserIdIgnoreCase(vendorId, keycloakUserId)) {
                throw new ValidationException("Vendor staff already exists for vendor and " + KEYCLOAK_USER_ID_FIELD);
            }
        } else if (vendorStaffAccessRepository.existsByVendorIdAndKeycloakUserIdIgnoreCaseAndIdNot(vendorId, keycloakUserId, entity.getId())) {
            throw new ValidationException("Another vendor staff row already exists for vendor and " + KEYCLOAK_USER_ID_FIELD);
        }
        entity.setVendorId(vendorId);
        entity.setKeycloakUserId(keycloakUserId);
        entity.setEmail(normalizeEmail(request.email(), EMAIL_FIELD));
        entity.setDisplayName(trimToNull(request.displayName()));
        entity.setPermissions(normalizeVendorPermissions(request.permissions()));
        entity.setActive(request.active() == null || request.active());
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setMfaRequired(request.mfaRequired() != null && request.mfaRequired());
        validatePermissionGroupScope(request.permissionGroupId(), PermissionGroupScope.VENDOR);
        entity.setPermissionGroupId(request.permissionGroupId());
        entity.setAccessExpiresAt(request.accessExpiresAt());
        entity.setAllowedIps(trimToNull(request.allowedIps()));
    }

    private void validatePermissionGroupScope(UUID permissionGroupId, PermissionGroupScope expectedScope) {
        if (permissionGroupId == null) {
            return;
        }
        PermissionGroup group = permissionGroupRepository.findById(permissionGroupId)
                .orElseThrow(() -> new ValidationException(PERMISSION_GROUP_NOT_FOUND + permissionGroupId));
        if (group.getScope() != expectedScope) {
            throw new ValidationException(
                    "Permission group '" + group.getName() + "' has scope " + group.getScope()
                            + " but expected " + expectedScope
            );
        }
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

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String truncate(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
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

    public void processAuditOutboxBatch(int batchSize) {
        List<AccessAuditOutboxEvent> events = accessAuditOutboxRepository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );
        for (AccessAuditOutboxEvent event : events) {
            processAuditOutboxEventInNewTransaction(event.getId());
        }
    }

    private void processAuditOutboxEventInNewTransaction(UUID eventId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> processAuditOutboxEventInternal(eventId));
    }

    private void processAuditOutboxEventInternal(UUID eventId) {
        AccessAuditOutboxEvent event = accessAuditOutboxRepository.findById(eventId).orElse(null);
        if (event == null || event.getProcessedAt() != null) {
            return;
        }

        try {
            if (!accessChangeAuditRepository.existsBySourceEventId(event.getId())) {
                accessChangeAuditRepository.save(AccessChangeAudit.builder()
                        .sourceEventId(event.getId())
                        .targetType(event.getTargetType())
                        .targetId(event.getTargetId())
                        .vendorId(event.getVendorId())
                        .keycloakUserId(trimToNull(event.getKeycloakUserId()))
                        .email(trimToNull(event.getEmail()))
                        .action(event.getAction())
                        .activeAfter(event.isActiveAfter())
                        .deletedAfter(event.isDeletedAfter())
                        .permissionsSnapshot(trimToNull(event.getPermissionsSnapshot()))
                        .actorSub(trimToNull(event.getActorSub()))
                        .actorTenantId(trimToNull(event.getActorTenantId()))
                        .actorRoles(trimToNull(event.getActorRoles()))
                        .actorType(defaultValue(event.getActorType(), SYSTEM_VALUE))
                        .changeSource(defaultValue(event.getChangeSource(), SYSTEM_VALUE))
                        .reason(trimToNull(event.getReason()))
                        .changeSet(trimToNull(event.getChangeSet()))
                        .clientIp(trimToNull(event.getClientIp()))
                        .userAgent(trimToNull(event.getUserAgent()))
                        .requestId(trimToNull(event.getRequestId()))
                        .build());
            }
            markAuditOutboxProcessed(event);
        } catch (DataIntegrityViolationException duplicate) {
            markAuditOutboxProcessed(event);
        } catch (Exception ex) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLastError(truncate(ex.getMessage(), 500));
            event.setAvailableAt(Instant.now().plusSeconds(resolveAuditRetryDelaySeconds(event.getAttemptCount())));
            accessAuditOutboxRepository.save(event);
            log.warn("Access audit outbox event {} failed on attempt {}", event.getId(), event.getAttemptCount(), ex);
        }
    }

    private void recordPlatformAudit(PlatformStaffAccess entity, Object beforeState, Object afterState, AuditCommand command) {
        if (entity == null || command.action() == null) {
            return;
        }
        AccessAuditRequestContext context = accessAuditRequestContextResolver.resolve(
                command.actorSub(),
                command.actorRoles(),
                command.changeSource()
        );
        enqueueAuditEvent(
                new AuditTarget("PLATFORM_STAFF", entity.getId(), null),
                new AuditState(
                        trimToNull(entity.getKeycloakUserId()),
                        accessAuditPayloadSanitizer.sanitizeEmail(entity.getEmail()),
                        entity.isActive(),
                        entity.isDeleted(),
                        joinPlatformPermissions(entity.getPermissions())
                ),
                command.action(),
                context,
                trimToNull(command.reason()),
                accessAuditPayloadSanitizer.buildChangeSet(beforeState, afterState)
        );
    }

    private void recordVendorAudit(VendorStaffAccess entity, Object beforeState, Object afterState, AuditCommand command) {
        if (entity == null || command.action() == null) {
            return;
        }
        AccessAuditRequestContext context = accessAuditRequestContextResolver.resolve(
                command.actorSub(),
                command.actorRoles(),
                command.changeSource()
        );
        enqueueAuditEvent(
                new AuditTarget("VENDOR_STAFF", entity.getId(), entity.getVendorId()),
                new AuditState(
                        trimToNull(entity.getKeycloakUserId()),
                        accessAuditPayloadSanitizer.sanitizeEmail(entity.getEmail()),
                        entity.isActive(),
                        entity.isDeleted(),
                        joinVendorPermissions(entity.getPermissions())
                ),
                command.action(),
                context,
                trimToNull(command.reason()),
                accessAuditPayloadSanitizer.buildChangeSet(beforeState, afterState)
        );
    }

    private void recordPermissionGroupAudit(
            PermissionGroup entity,
            Object beforeState,
            Object afterState,
            AccessChangeAction action,
            String changeSource
    ) {
        if (entity == null || action == null) {
            return;
        }
        AccessAuditRequestContext context = accessAuditRequestContextResolver.resolve(null, null, changeSource);
        enqueueAuditEvent(
                new AuditTarget("PERMISSION_GROUP", entity.getId(), null),
                new AuditState(
                        null,
                        null,
                        action != AccessChangeAction.DELETED,
                        action == AccessChangeAction.DELETED,
                        trimToNull(entity.getPermissions())
                ),
                action,
                context,
                null,
                accessAuditPayloadSanitizer.buildChangeSet(beforeState, afterState)
        );
    }

    private void enqueueAuditEvent(
            AuditTarget target,
            AuditState state,
            AccessChangeAction action,
            AccessAuditRequestContext context,
            String reason,
            String changeSet
    ) {
        accessAuditOutboxRepository.save(AccessAuditOutboxEvent.builder()
                .targetType(target.targetType())
                .targetId(target.targetId())
                .vendorId(target.vendorId())
                .keycloakUserId(state.keycloakUserId())
                .email(state.email())
                .action(action)
                .activeAfter(state.activeAfter())
                .deletedAfter(state.deletedAfter())
                .permissionsSnapshot(state.permissionsSnapshot())
                .actorSub(trimToNull(context.actorSub()))
                .actorTenantId(trimToNull(context.actorTenantId()))
                .actorRoles(trimToNull(context.actorRoles()))
                .actorType(defaultValue(context.actorType(), SYSTEM_VALUE))
                .changeSource(defaultValue(context.changeSource(), SYSTEM_VALUE))
                .reason(reason)
                .changeSet(changeSet)
                .clientIp(trimToNull(context.clientIp()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    private void markAuditOutboxProcessed(AccessAuditOutboxEvent event) {
        event.setProcessedAt(Instant.now());
        event.setLastError(null);
        accessAuditOutboxRepository.save(event);
    }

    private long resolveAuditRetryDelaySeconds(int attemptCount) {
        long base = Math.max(5L, accessAuditRetryBaseDelaySeconds);
        long multiplier = Math.max(1L, attemptCount);
        return Math.min(900L, base * multiplier * multiplier);
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
                entity.getActorTenantId(),
                entity.getActorRoles(),
                entity.getActorType(),
                entity.getChangeSource(),
                entity.getReason(),
                entity.getChangeSet(),
                entity.getClientIp(),
                entity.getUserAgent(),
                entity.getRequestId(),
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

    private AccessService self() {
        return selfProvider.getObject();
    }

    private boolean isExpired(Instant accessExpiresAt) {
        return accessExpiresAt != null && Instant.now().isAfter(accessExpiresAt);
    }

    private void verifyVendorTenancy(VendorStaffAccess entity, UUID callerVendorId) {
        if (callerVendorId != null && !callerVendorId.equals(entity.getVendorId())) {
            throw new ValidationException("Vendor staff does not belong to the specified vendor");
        }
    }

    private String escapeLikePattern(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    // ── Permission Groups ──────────────────────────────────────────────

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
                .orElseThrow(() -> new ResourceNotFoundException(PERMISSION_GROUP_NOT_FOUND + id));
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
        PermissionGroup saved = permissionGroupRepository.save(entity);
        recordPermissionGroupAudit(saved, null, toPermissionGroupResponse(saved), AccessChangeAction.CREATED, ADMIN_API_SOURCE);
        return toPermissionGroupResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PermissionGroupResponse updatePermissionGroup(UUID id, UpsertPermissionGroupRequest request) {
        PermissionGroup entity = permissionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PERMISSION_GROUP_NOT_FOUND + id));
        PermissionGroupResponse beforeState = toPermissionGroupResponse(entity);
        String name = normalizeRequired(request.name(), "name", 120);
        if (permissionGroupRepository.existsByNameIgnoreCaseAndScopeAndIdNot(name, request.scope(), id)) {
            throw new ValidationException("Another permission group with this name and scope already exists");
        }
        if (entity.getScope() != request.scope()) {
            boolean hasPlatformAssignments = platformStaffAccessRepository.existsByPermissionGroupIdAndDeletedFalse(id);
            boolean hasVendorAssignments = vendorStaffAccessRepository.existsByPermissionGroupIdAndDeletedFalse(id);
            if (hasPlatformAssignments || hasVendorAssignments) {
                throw new ValidationException(
                        "Cannot change scope of permission group that has active staff assignments. "
                                + "Remove all assignments first or create a new group.");
            }
        }
        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity.setPermissions(joinStringSet(request.permissions()));
        entity.setScope(request.scope());
        PermissionGroup saved = permissionGroupRepository.save(entity);
        recordPermissionGroupAudit(saved, beforeState, toPermissionGroupResponse(saved), AccessChangeAction.UPDATED, ADMIN_API_SOURCE);
        return toPermissionGroupResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deletePermissionGroup(UUID id) {
        PermissionGroup entity = permissionGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PERMISSION_GROUP_NOT_FOUND + id));
        boolean hasPlatformAssignments = platformStaffAccessRepository.existsByPermissionGroupIdAndDeletedFalse(id);
        boolean hasVendorAssignments = vendorStaffAccessRepository.existsByPermissionGroupIdAndDeletedFalse(id);
        if (hasPlatformAssignments || hasVendorAssignments) {
            throw new ValidationException(
                    "Cannot delete permission group with active staff assignments. "
                            + "Reassign or remove staff from this group first.");
        }
        PermissionGroupResponse beforeState = toPermissionGroupResponse(entity);
        permissionGroupRepository.deleteById(id);
        recordPermissionGroupAudit(entity, beforeState, null, AccessChangeAction.DELETED, ADMIN_API_SOURCE);
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
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public ActiveSessionResponse registerSession(RegisterSessionRequest request) {
        String keycloakId = normalizeRequired(request.keycloakId(), KEYCLOAK_ID_FIELD, 120);
        String keycloakSessionId = normalizeRequired(request.keycloakSessionId(), KEYCLOAK_SESSION_ID_FIELD, 120);
        String ipAddress = trimToNull(request.ipAddress());
        String userAgent = trimToNull(request.userAgent());

        for (int attempt = 1; attempt <= SESSION_SYNC_MAX_ATTEMPTS; attempt++) {
            try {
                return registerSessionOnce(keycloakId, keycloakSessionId, ipAddress, userAgent);
            } catch (TransientDataAccessException ex) {
                if (attempt >= SESSION_SYNC_MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn(
                        "Transient active session write conflict for keycloakId={} keycloakSessionId={} attempt={}/{}",
                        keycloakId,
                        keycloakSessionId,
                        attempt,
                        SESSION_SYNC_MAX_ATTEMPTS
                );
            }
        }

        throw new IllegalStateException("Unable to register active session");
    }

    @Override
    public Page<ActiveSessionResponse> listSessionsByKeycloakId(String keycloakId, Pageable pageable) {
        String normalized = normalizeRequired(keycloakId, KEYCLOAK_ID_FIELD, 120);
        return activeSessionRepository.findByKeycloakIdIgnoreCaseOrderByLastActivityAtDesc(normalized, pageable)
                .map(this::toActiveSessionResponse);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeSession(UUID sessionId) {
        ActiveSession session = activeSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        String keycloakSessionId = normalizeRequired(session.getKeycloakSessionId(), KEYCLOAK_SESSION_ID_FIELD, 120);
        activeSessionRepository.delete(session);
        gatewaySessionClient.revokeSessionByKeycloakSessionId(keycloakSessionId);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void revokeOwnSession(UUID sessionId, String keycloakId) {
        String normalizedKeycloakId = normalizeRequired(keycloakId, KEYCLOAK_ID_FIELD, 120);
        ActiveSession session = activeSessionRepository.findByIdAndKeycloakIdIgnoreCase(sessionId, normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        String keycloakSessionId = normalizeRequired(session.getKeycloakSessionId(), KEYCLOAK_SESSION_ID_FIELD, 120);
        activeSessionRepository.delete(session);
        gatewaySessionClient.revokeSessionByKeycloakSessionId(keycloakSessionId);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeSessionByKeycloakSessionId(String keycloakSessionId) {
        revokeSessionByKeycloakSessionIdInternal(keycloakSessionId, true);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeSessionByKeycloakSessionIdFromGateway(String keycloakSessionId) {
        revokeSessionByKeycloakSessionIdInternal(keycloakSessionId, false);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeAllSessions(String keycloakId) {
        revokeAllSessionsInternal(keycloakId, true);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void revokeAllSessionsFromGateway(String keycloakId) {
        revokeAllSessionsInternal(keycloakId, false);
    }

    private ActiveSessionResponse toActiveSessionResponse(ActiveSession entity) {
        return new ActiveSessionResponse(
                entity.getId(),
                entity.getKeycloakId(),
                entity.getKeycloakSessionId(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getLastActivityAt(),
                entity.getCreatedAt()
        );
    }

    private void revokeSessionByKeycloakSessionIdInternal(String keycloakSessionId, boolean propagateToGateway) {
        String normalized = normalizeRequired(keycloakSessionId, KEYCLOAK_SESSION_ID_FIELD, 120);
        ActiveSession existing = activeSessionRepository.findByKeycloakSessionIdIgnoreCase(normalized).orElse(null);
        if (existing != null) {
            activeSessionRepository.delete(existing);
        }
        if (propagateToGateway) {
            gatewaySessionClient.revokeSessionByKeycloakSessionId(normalized);
        }
    }

    private void revokeAllSessionsInternal(String keycloakId, boolean propagateToGateway) {
        String normalized = normalizeRequired(keycloakId, KEYCLOAK_ID_FIELD, 120);
        List<ActiveSession> activeSessions = activeSessionRepository.findByKeycloakIdIgnoreCaseOrderByLastActivityAtDesc(normalized);
        if (!activeSessions.isEmpty()) {
            activeSessionRepository.deleteAllInBatch(activeSessions);
        }
        if (propagateToGateway) {
            gatewaySessionClient.revokeAllSessionsForKeycloakUser(normalized);
        }
    }

    private ActiveSessionResponse registerSessionOnce(
            String keycloakId,
            String keycloakSessionId,
            String ipAddress,
            String userAgent
    ) {
        Instant now = Instant.now();
        ActiveSession existing = activeSessionRepository.findByKeycloakSessionIdIgnoreCase(keycloakSessionId).orElse(null);
        if (existing != null) {
            if (shouldSkipSessionTouch(existing, keycloakId, ipAddress, userAgent, now)) {
                return toActiveSessionResponse(existing);
            }
            activeSessionRepository.touchByKeycloakSessionId(keycloakId, keycloakSessionId, ipAddress, userAgent, now);
            return activeSessionRepository.findByKeycloakSessionIdIgnoreCase(keycloakSessionId)
                    .map(this::toActiveSessionResponse)
                    .orElseGet(() -> buildDetachedSessionResponse(existing, keycloakId, keycloakSessionId, ipAddress, userAgent, now));
        }

        try {
            ActiveSession created = activeSessionRepository.save(ActiveSession.builder()
                    .keycloakId(keycloakId)
                    .keycloakSessionId(keycloakSessionId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .lastActivityAt(now)
                    .build());
            return toActiveSessionResponse(created);
        } catch (DataIntegrityViolationException ex) {
            ActiveSession concurrent = activeSessionRepository.findByKeycloakSessionIdIgnoreCase(keycloakSessionId)
                    .orElseThrow(() -> ex);
            if (shouldSkipSessionTouch(concurrent, keycloakId, ipAddress, userAgent, now)) {
                return toActiveSessionResponse(concurrent);
            }
            activeSessionRepository.touchByKeycloakSessionId(keycloakId, keycloakSessionId, ipAddress, userAgent, now);
            return activeSessionRepository.findByKeycloakSessionIdIgnoreCase(keycloakSessionId)
                    .map(this::toActiveSessionResponse)
                    .orElseGet(() -> buildDetachedSessionResponse(concurrent, keycloakId, keycloakSessionId, ipAddress, userAgent, now));
        }
    }

    private boolean shouldSkipSessionTouch(
            ActiveSession session,
            String keycloakId,
            String ipAddress,
            String userAgent,
            Instant now
    ) {
        if (session == null) {
            return false;
        }
        if (!Objects.equals(trimToNull(session.getKeycloakId()), keycloakId)) {
            return false;
        }
        if (!Objects.equals(trimToNull(session.getIpAddress()), ipAddress)) {
            return false;
        }
        if (!Objects.equals(trimToNull(session.getUserAgent()), userAgent)) {
            return false;
        }
        Instant lastActivityAt = session.getLastActivityAt();
        return lastActivityAt != null
                && !lastActivityAt.isBefore(now.minusSeconds(SESSION_ACTIVITY_WRITE_THROTTLE_SECONDS));
    }

    private ActiveSessionResponse buildDetachedSessionResponse(
            ActiveSession source,
            String keycloakId,
            String keycloakSessionId,
            String ipAddress,
            String userAgent,
            Instant lastActivityAt
    ) {
        return new ActiveSessionResponse(
                source.getId(),
                keycloakId,
                keycloakSessionId,
                ipAddress,
                userAgent,
                lastActivityAt,
                source.getCreatedAt()
        );
    }

    // ── API Key Management ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CreateApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        String keycloakId = normalizeRequired(request.keycloakId(), KEYCLOAK_ID_FIELD, 120);
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
    public Page<ApiKeyResponse> listApiKeys(String keycloakId, Pageable pageable) {
        String normalized = normalizeRequired(keycloakId, KEYCLOAK_ID_FIELD, 120);
        return apiKeyRepository.findByKeycloakId(normalized, pageable)
                .map(this::toApiKeyResponse);
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

    private record AuditCommand(
            AccessChangeAction action,
            String actorSub,
            String actorRoles,
            String reason,
            String changeSource
    ) {
    }

    private record AuditTarget(String targetType, UUID targetId, UUID vendorId) {
    }

    private record AuditState(
            String keycloakUserId,
            String email,
            boolean activeAfter,
            boolean deletedAfter,
            String permissionsSnapshot
    ) {
    }

}
