package com.rumal.access_service.service;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.dto.AccessChangeAuditResponse;
import com.rumal.access_service.dto.PlatformAccessLookupResponse;
import com.rumal.access_service.dto.PlatformStaffAccessResponse;
import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.access_service.dto.VendorStaffAccessResponse;
import com.rumal.access_service.entity.AccessChangeAction;
import com.rumal.access_service.entity.AccessChangeAudit;
import com.rumal.access_service.entity.PlatformPermission;
import com.rumal.access_service.entity.PlatformStaffAccess;
import com.rumal.access_service.entity.VendorPermission;
import com.rumal.access_service.entity.VendorStaffAccess;
import com.rumal.access_service.exception.ResourceNotFoundException;
import com.rumal.access_service.exception.ValidationException;
import com.rumal.access_service.repo.PlatformStaffAccessRepository;
import com.rumal.access_service.repo.VendorStaffAccessRepository;
import com.rumal.access_service.repo.AccessChangeAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
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

    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;
    private final AccessChangeAuditRepository accessChangeAuditRepository;
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
    public List<PlatformStaffAccessResponse> listDeletedPlatformStaff() {
        return platformStaffAccessRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toPlatformStaffResponse).toList();
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
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(saved, AccessChangeAction.CREATED, actorSub, actorRoles, reason);
        evictPlatformAccessLookup(saved.getKeycloakUserId());
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
        evictPlatformAccessLookup(previousKeycloakUserId);
        evictPlatformAccessLookup(saved.getKeycloakUserId());
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
        evictPlatformAccessLookup(saved.getKeycloakUserId());
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
        PlatformStaffAccess saved = platformStaffAccessRepository.save(entity);
        recordPlatformAudit(saved, AccessChangeAction.RESTORED, actorSub, actorRoles, reason);
        evictPlatformAccessLookup(saved.getKeycloakUserId());
        return toPlatformStaffResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "platformAccessLookup", key = "#keycloakUserId == null ? '' : #keycloakUserId.trim().toLowerCase()")
    public PlatformAccessLookupResponse getPlatformAccessByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return platformStaffAccessRepository.findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalse(normalized)
                .map(entity -> new PlatformAccessLookupResponse(
                        entity.getKeycloakUserId(),
                        entity.isActive(),
                        entity.getPermissions().stream().map(PlatformPermission::code).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                ))
                .orElseGet(() -> new PlatformAccessLookupResponse(normalized, false, Set.of()));
    }

    @Override
    public List<VendorStaffAccessResponse> listVendorStaff(UUID vendorId) {
        if (vendorId == null) {
            return listAllVendorStaff();
        }
        return vendorStaffAccessRepository.findByVendorIdAndDeletedFalseOrderByEmailAsc(vendorId).stream().map(this::toVendorStaffResponse).toList();
    }

    @Override
    public List<VendorStaffAccessResponse> listAllVendorStaff() {
        return vendorStaffAccessRepository.findByDeletedFalseOrderByVendorIdAscEmailAsc().stream().map(this::toVendorStaffResponse).toList();
    }

    @Override
    public List<VendorStaffAccessResponse> listDeletedVendorStaff() {
        return vendorStaffAccessRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toVendorStaffResponse).toList();
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
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(saved, AccessChangeAction.CREATED, actorSub, actorRoles, reason);
        evictVendorAccessLookup(saved.getKeycloakUserId());
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
        evictVendorAccessLookup(previousKeycloakUserId);
        evictVendorAccessLookup(saved.getKeycloakUserId());
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
        evictVendorAccessLookup(saved.getKeycloakUserId());
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
        VendorStaffAccess saved = vendorStaffAccessRepository.save(entity);
        recordVendorAudit(saved, AccessChangeAction.RESTORED, actorSub, actorRoles, reason);
        evictVendorAccessLookup(saved.getKeycloakUserId());
        return toVendorStaffResponse(saved);
    }

    @Override
    @Cacheable(cacheNames = "vendorAccessLookup", key = "#keycloakUserId == null ? '' : #keycloakUserId.trim().toLowerCase()")
    public List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return vendorStaffAccessRepository.findByKeycloakUserIdIgnoreCaseAndActiveTrueAndDeletedFalseOrderByVendorIdAsc(normalized)
                .stream()
                .map(entity -> new VendorStaffAccessLookupResponse(
                        entity.getVendorId(),
                        entity.getKeycloakUserId(),
                        entity.isActive(),
                        entity.getPermissions().stream().map(VendorPermission::code).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                ))
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
}
