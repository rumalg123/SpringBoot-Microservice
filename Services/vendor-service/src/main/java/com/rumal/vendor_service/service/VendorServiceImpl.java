package com.rumal.vendor_service.service;

import com.rumal.vendor_service.client.OrderLifecycleClient;
import com.rumal.vendor_service.client.ProductCatalogAdminClient;
import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorOperationalStateResponse;
import com.rumal.vendor_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorLifecycleAction;
import com.rumal.vendor_service.entity.VendorLifecycleAudit;
import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.entity.VendorUser;
import com.rumal.vendor_service.exception.ResourceNotFoundException;
import com.rumal.vendor_service.exception.ServiceUnavailableException;
import com.rumal.vendor_service.exception.ValidationException;
import com.rumal.vendor_service.repo.VendorLifecycleAuditRepository;
import com.rumal.vendor_service.repo.VendorRepository;
import com.rumal.vendor_service.repo.VendorUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final VendorLifecycleAuditRepository vendorLifecycleAuditRepository;
    private final VendorUserRepository vendorUserRepository;
    private final OrderLifecycleClient orderLifecycleClient;
    private final ProductCatalogAdminClient productCatalogAdminClient;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Value("${vendor.delete.refund-hold-days:14}")
    private int vendorDeleteRefundHoldDays;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse create(UpsertVendorRequest request) {
        Vendor vendor = new Vendor();
        applyVendorRequest(vendor, request);
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.CREATED, null, null, null);
        return toVendorResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public VendorResponse update(UUID id, UpsertVendorRequest request) {
        Vendor vendor = getNonDeletedVendor(id);
        applyVendorRequest(vendor, request);
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.UPDATED, null, null, null);
        syncProductCatalogVisibility(saved.getId());
        return toVendorResponse(saved);
    }

    @Override
    public VendorResponse getByIdOrSlug(String idOrSlug) {
        Vendor vendor = findByIdOrSlug(idOrSlug);
        if (!isStorefrontVisible(vendor)) {
            throw new ResourceNotFoundException("Vendor not found: " + idOrSlug);
        }
        return toVendorResponse(vendor);
    }

    @Override
    public VendorResponse getAdminById(UUID id) {
        return toVendorResponse(vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id)));
    }

    @Override
    public List<VendorResponse> listPublicActive() {
        return vendorRepository.findByDeletedFalseAndActiveTrueAndStatusOrderByNameAsc(VendorStatus.ACTIVE)
                .stream()
                .filter(Vendor::isAcceptingOrders)
                .map(this::toVendorResponse)
                .toList();
    }

    @Override
    public List<VendorResponse> listAllNonDeleted() {
        return vendorRepository.findByDeletedFalseOrderByNameAsc().stream().map(this::toVendorResponse).toList();
    }

    @Override
    public List<VendorResponse> listDeleted() {
        return vendorRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toVendorResponse).toList();
    }

    @Override
    public List<VendorLifecycleAuditResponse> listLifecycleAudit(UUID id) {
        vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
        return vendorLifecycleAuditRepository.findByVendorIdOrderByCreatedAtDesc(id)
                .stream()
                .map(this::toVendorLifecycleAuditResponse)
                .toList();
    }

    @Override
    public VendorDeletionEligibilityResponse getDeletionEligibility(UUID id) {
        Vendor vendor = getNonDeletedVendor(id);
        VendorOrderDeletionCheckResponse orderCheck = orderLifecycleClient
                .getVendorDeletionCheck(vendor.getId(), Math.max(0, vendorDeleteRefundHoldDays), requireInternalAuth());

        Instant lastOrderAt = orderCheck.lastOrderAt();
        Instant refundHoldUntil = lastOrderAt == null
                ? null
                : lastOrderAt.plus(Duration.ofDays(Math.max(0, vendorDeleteRefundHoldDays)));

        List<String> blockingReasons = new ArrayList<>();
        if (orderCheck.pendingOrders() > 0) {
            blockingReasons.add("PENDING_ORDERS");
        }
        if (refundHoldUntil != null && Instant.now().isBefore(refundHoldUntil)) {
            blockingReasons.add("REFUND_HOLD_WINDOW");
        }

        return new VendorDeletionEligibilityResponse(
                vendor.getId(),
                blockingReasons.isEmpty(),
                Math.max(0, orderCheck.totalOrders()),
                Math.max(0, orderCheck.pendingOrders()),
                lastOrderAt,
                refundHoldUntil,
                List.copyOf(blockingReasons)
        );
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDelete(UUID id) {
        softDelete(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDelete(UUID id, String reason, String actorSub, String actorRoles) {
        // Keep method for backward compatibility with internal callers, but enforce two-step semantics.
        confirmDelete(id, reason, actorSub, actorRoles);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse requestDelete(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(id);
        if (vendor.getDeletionRequestedAt() != null) {
            return toVendorResponse(vendor);
        }
        vendor.setDeletionRequestedAt(Instant.now());
        vendor.setDeletionRequestReason(trimToNull(reason));
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.DELETE_REQUESTED, reason, actorSub, actorRoles);
        return toVendorResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public void confirmDelete(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
        if (vendor.isDeleted()) {
            return;
        }
        if (vendor.getDeletionRequestedAt() == null) {
            throw new ValidationException("Delete request must be created before confirm delete");
        }
        VendorDeletionEligibilityResponse eligibility = getDeletionEligibility(id);
        if (!eligibility.eligible()) {
            String blocker = eligibility.blockingReasons().isEmpty()
                    ? "Vendor cannot be deleted yet"
                    : String.join(", ", eligibility.blockingReasons());
            throw new ValidationException("Vendor cannot be deleted: " + blocker);
        }
        vendor.setDeleted(true);
        vendor.setDeletedAt(Instant.now());
        vendor.setActive(false);
        vendor.setAcceptingOrders(false);
        if (StringUtils.hasText(reason)) {
            vendor.setDeletionRequestReason(trimToNull(reason));
        }
        vendorRepository.save(vendor);
        recordLifecycleAudit(vendor, VendorLifecycleAction.DELETE_CONFIRMED, reason, actorSub, actorRoles);
        syncProductCatalogVisibility(vendor.getId());
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse stopReceivingOrders(UUID id) {
        return stopReceivingOrders(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public VendorResponse stopReceivingOrders(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(id);
        if (!vendor.isAcceptingOrders()) {
            return toVendorResponse(vendor);
        }
        vendor.setAcceptingOrders(false);
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.STOP_ORDERS, reason, actorSub, actorRoles);
        syncProductCatalogVisibility(saved.getId());
        return toVendorResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse resumeReceivingOrders(UUID id) {
        return resumeReceivingOrders(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public VendorResponse resumeReceivingOrders(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(id);
        if (!vendor.isActive()) {
            throw new ValidationException("Cannot resume orders for an inactive vendor");
        }
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new ValidationException("Cannot resume orders unless vendor status is ACTIVE");
        }
        if (vendor.isAcceptingOrders()) {
            return toVendorResponse(vendor);
        }
        vendor.setAcceptingOrders(true);
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.RESUME_ORDERS, reason, actorSub, actorRoles);
        syncProductCatalogVisibility(saved.getId());
        return toVendorResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse restore(UUID id) {
        return restore(id, null, null, null);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public VendorResponse restore(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
        if (!vendor.isDeleted()) {
            return toVendorResponse(vendor);
        }
        vendor.setDeleted(false);
        vendor.setDeletedAt(null);
        vendor.setDeletionRequestedAt(null);
        vendor.setDeletionRequestReason(null);
        vendor.setAcceptingOrders(false);
        Vendor saved = vendorRepository.save(vendor);
        recordLifecycleAudit(saved, VendorLifecycleAction.RESTORED, reason, actorSub, actorRoles);
        syncProductCatalogVisibility(saved.getId());
        return toVendorResponse(saved);
    }

    @Override
    public boolean isSlugAvailable(String slug, UUID excludeId) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (!StringUtils.hasText(normalizedSlug)) {
            return false;
        }
        return excludeId == null
                ? !vendorRepository.existsBySlug(normalizedSlug)
                : !vendorRepository.existsBySlugAndIdNot(normalizedSlug, excludeId);
    }

    @Override
    public List<VendorUserResponse> listVendorUsers(UUID vendorId) {
        getNonDeletedVendor(vendorId);
        return vendorUserRepository.findByVendorIdOrderByRoleAscCreatedAtAsc(vendorId)
                .stream()
                .map(this::toVendorUserResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorUserResponse addVendorUser(UUID vendorId, UpsertVendorUserRequest request) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        String keycloakUserId = normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120);
        if (vendorUserRepository.existsByVendorIdAndKeycloakUserId(vendorId, keycloakUserId)) {
            throw new ValidationException("Vendor user already exists for vendor and keycloakUserId");
        }
        VendorUser user = new VendorUser();
        user.setVendor(vendor);
        applyVendorUserRequest(user, request);
        return toVendorUserResponse(vendorUserRepository.save(user));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorUserResponse updateVendorUser(UUID vendorId, UUID membershipId, UpsertVendorUserRequest request) {
        getNonDeletedVendor(vendorId);
        VendorUser user = vendorUserRepository.findByIdAndVendorId(membershipId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor user membership not found: " + membershipId));
        String incomingKeycloak = normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120);
        if (!incomingKeycloak.equalsIgnoreCase(user.getKeycloakUserId())
                && vendorUserRepository.existsByVendorIdAndKeycloakUserId(vendorId, incomingKeycloak)) {
            throw new ValidationException("Another vendor user already exists with this keycloakUserId");
        }
        applyVendorUserRequest(user, request);
        return toVendorUserResponse(vendorUserRepository.save(user));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void removeVendorUser(UUID vendorId, UUID membershipId) {
        getNonDeletedVendor(vendorId);
        VendorUser user = vendorUserRepository.findByIdAndVendorId(membershipId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor user membership not found: " + membershipId));
        vendorUserRepository.delete(user);
    }

    @Override
    public List<VendorAccessMembershipResponse> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return vendorUserRepository
                .findByKeycloakUserIdIgnoreCaseAndActiveTrueAndVendorDeletedFalseAndVendorActiveTrueOrderByCreatedAtAsc(normalized)
                .stream()
                .map(user -> new VendorAccessMembershipResponse(
                        user.getVendor().getId(),
                        user.getVendor().getSlug(),
                        user.getVendor().getName(),
                        user.getRole()
                ))
                .toList();
    }

    @Override
    @Cacheable(cacheNames = "vendorOperationalState", key = "#vendorId")
    public VendorOperationalStateResponse getOperationalState(UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + vendorId));
        return toOperationalState(vendor);
    }

    @Override
    public List<VendorOperationalStateResponse> getOperationalStates(List<UUID> vendorIds) {
        if (vendorIds == null || vendorIds.isEmpty()) {
            return List.of();
        }
        return vendorRepository.findAllById(vendorIds).stream()
                .map(this::toOperationalState)
                .toList();
    }

    private Vendor findByIdOrSlug(String idOrSlug) {
        UUID id = tryParseUuid(idOrSlug);
        if (id != null) {
            return vendorRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + idOrSlug));
        }
        String slug = normalizeRequestedSlug(idOrSlug);
        return vendorRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + idOrSlug));
    }

    private Vendor getNonDeletedVendor(UUID id) {
        return vendorRepository.findById(id)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
    }

    private UUID tryParseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyVendorRequest(Vendor vendor, UpsertVendorRequest request) {
        String name = normalizeRequired(request.name(), "name", 160);
        String requestedSlug = normalizeRequestedSlug(request.slug());
        boolean autoSlug = !StringUtils.hasText(requestedSlug);
        String baseSlug = autoSlug ? SlugUtils.toSlug(name) : requestedSlug;
        String resolvedSlug = resolveUniqueSlug(baseSlug, vendor.getId(), autoSlug);

        vendor.setName(name);
        vendor.setNormalizedName(name.toLowerCase(Locale.ROOT));
        vendor.setSlug(resolvedSlug);
        vendor.setContactEmail(normalizeEmailRequired(request.contactEmail(), "contactEmail"));
        vendor.setSupportEmail(normalizeEmailOptional(request.supportEmail()));
        vendor.setContactPhone(trimToNull(request.contactPhone()));
        vendor.setContactPersonName(trimToNull(request.contactPersonName()));
        vendor.setLogoImage(trimToNull(request.logoImage()));
        vendor.setBannerImage(trimToNull(request.bannerImage()));
        vendor.setWebsiteUrl(trimToNull(request.websiteUrl()));
        vendor.setDescription(trimToNull(request.description()));
        VendorStatus status = Objects.requireNonNull(request.status(), "status");
        boolean active = request.active() == null || request.active();
        boolean acceptingOrders = request.acceptingOrders() != null
                ? request.acceptingOrders()
                : (vendor.getId() == null ? true : vendor.isAcceptingOrders());
        if (!active || status != VendorStatus.ACTIVE) {
            acceptingOrders = false;
        }
        vendor.setStatus(status);
        vendor.setActive(active);
        vendor.setAcceptingOrders(acceptingOrders);
        vendor.setDeleted(false);
        vendor.setDeletedAt(null);
    }

    private void applyVendorUserRequest(VendorUser user, UpsertVendorUserRequest request) {
        user.setKeycloakUserId(normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120));
        user.setEmail(normalizeEmailRequired(request.email(), "email"));
        user.setDisplayName(trimToNull(request.displayName()));
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
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

    private String normalizeEmailRequired(String value, String fieldName) {
        String trimmed = normalizeRequired(value, fieldName, 180);
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeEmailOptional(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequestedSlug(String slug) {
        String normalized = SlugUtils.toSlug(slug);
        return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
    }

    private String resolveUniqueSlug(String baseSlug, UUID existingId, boolean allowAutoSuffix) {
        String seed = StringUtils.hasText(baseSlug) ? baseSlug : "vendor";
        if (isSlugAvailable(seed, existingId)) {
            return seed;
        }
        if (!allowAutoSuffix) {
            throw new ValidationException("Vendor slug must be unique: " + seed);
        }
        int suffix = 2;
        while (suffix < 100_000) {
            String candidate = appendSlugSuffix(seed, suffix, 180);
            if (isSlugAvailable(candidate, existingId)) {
                return candidate;
            }
            suffix++;
        }
        throw new ValidationException("Unable to generate a unique vendor slug");
    }

    private String appendSlugSuffix(String slug, int suffix, int maxLen) {
        String suffixPart = "-" + suffix;
        int allowed = Math.max(1, maxLen - suffixPart.length());
        String base = slug.length() > allowed ? slug.substring(0, allowed) : slug;
        return base + suffixPart;
    }

    private VendorResponse toVendorResponse(Vendor v) {
        return new VendorResponse(
                v.getId(),
                v.getName(),
                v.getSlug(),
                v.getContactEmail(),
                v.getSupportEmail(),
                v.getContactPhone(),
                v.getContactPersonName(),
                v.getLogoImage(),
                v.getBannerImage(),
                v.getWebsiteUrl(),
                v.getDescription(),
                v.getStatus(),
                v.isActive(),
                v.isAcceptingOrders(),
                v.isDeleted(),
                v.getDeletedAt(),
                v.getDeletionRequestedAt(),
                v.getDeletionRequestReason(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    private VendorLifecycleAuditResponse toVendorLifecycleAuditResponse(VendorLifecycleAudit audit) {
        return new VendorLifecycleAuditResponse(
                audit.getId(),
                audit.getVendor().getId(),
                audit.getAction(),
                audit.getActorSub(),
                audit.getActorRoles(),
                audit.getActorType(),
                audit.getChangeSource(),
                audit.getReason(),
                audit.getCreatedAt()
        );
    }

    private VendorUserResponse toVendorUserResponse(VendorUser user) {
        return new VendorUserResponse(
                user.getId(),
                user.getVendor().getId(),
                user.getKeycloakUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private VendorOperationalStateResponse toOperationalState(Vendor vendor) {
        return new VendorOperationalStateResponse(
                vendor.getId(),
                vendor.isActive(),
                vendor.isDeleted(),
                vendor.getStatus(),
                vendor.isAcceptingOrders(),
                isStorefrontVisible(vendor)
        );
    }

    private boolean isStorefrontVisible(Vendor vendor) {
        return vendor != null
                && !vendor.isDeleted()
                && vendor.isActive()
                && vendor.getStatus() == VendorStatus.ACTIVE
                && vendor.isAcceptingOrders();
    }

    private String requireInternalAuth() {
        if (!StringUtils.hasText(internalAuthSharedSecret)) {
            throw new ServiceUnavailableException("INTERNAL_AUTH_SHARED_SECRET is not configured in vendor-service");
        }
        return internalAuthSharedSecret;
    }

    private void syncProductCatalogVisibility(UUID vendorId) {
        if (vendorId == null) {
            return;
        }
        productCatalogAdminClient.evictPublicCachesForVendor(vendorId, requireInternalAuth());
    }

    private void recordLifecycleAudit(Vendor vendor, VendorLifecycleAction action, String reason, String actorSub, String actorRoles) {
        if (vendor == null || action == null) {
            return;
        }
        VendorLifecycleAudit audit = VendorLifecycleAudit.builder()
                .vendor(vendor)
                .action(action)
                .actorSub(trimToNull(actorSub))
                .actorRoles(trimToNull(actorRoles))
                .actorType(StringUtils.hasText(actorSub) ? "USER" : "SYSTEM")
                .changeSource("ADMIN_API")
                .reason(trimToNull(reason))
                .build();
        vendorLifecycleAuditRepository.save(audit);
    }
}
