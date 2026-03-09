package com.rumal.vendor_service.service;

import com.rumal.vendor_service.client.OrderLifecycleClient;
import com.rumal.vendor_service.client.ProductCatalogAdminClient;
import com.rumal.vendor_service.dto.AdminVerificationActionRequest;
import com.rumal.vendor_service.dto.RequestVerificationRequest;
import com.rumal.vendor_service.dto.UpdateVendorMetricsRequest;
import com.rumal.vendor_service.dto.UpdateVendorSelfServiceRequest;
import com.rumal.vendor_service.dto.UpsertVendorPayoutConfigRequest;
import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorOperationalStateResponse;
import com.rumal.vendor_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.vendor_service.dto.VendorPayoutConfigResponse;
import com.rumal.vendor_service.dto.PublicVendorResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;
import com.rumal.vendor_service.entity.VendorAuditOutboxEvent;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorLifecycleAction;
import com.rumal.vendor_service.entity.VendorLifecycleAudit;
import com.rumal.vendor_service.entity.VendorPayoutConfig;
import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.entity.VendorUser;
import com.rumal.vendor_service.entity.VendorUserRole;
import com.rumal.vendor_service.entity.VerificationStatus;
import com.rumal.vendor_service.exception.ResourceNotFoundException;
import com.rumal.vendor_service.exception.ServiceUnavailableException;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.exception.ValidationException;
import com.rumal.vendor_service.repo.VendorAuditOutboxRepository;
import com.rumal.vendor_service.repo.VendorLifecycleAuditRepository;
import com.rumal.vendor_service.repo.VendorPayoutConfigRepository;
import com.rumal.vendor_service.repo.VendorRepository;
import com.rumal.vendor_service.repo.VendorUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class VendorServiceImpl implements VendorService {
    private static final Logger log = LoggerFactory.getLogger(VendorServiceImpl.class);

    private final VendorRepository vendorRepository;
    private final VendorLifecycleAuditRepository vendorLifecycleAuditRepository;
    private final VendorAuditOutboxRepository vendorAuditOutboxRepository;
    private final VendorUserRepository vendorUserRepository;
    private final VendorPayoutConfigRepository vendorPayoutConfigRepository;
    private final OrderLifecycleClient orderLifecycleClient;
    private final ProductCatalogAdminClient productCatalogAdminClient;
    private final TransactionTemplate transactionTemplate;
    private final VendorAuditRequestContextResolver vendorAuditRequestContextResolver;
    private final VendorAuditPayloadSanitizer vendorAuditPayloadSanitizer;

    @Lazy
    @Autowired
    private VendorServiceImpl self;

    @Value("${internal.auth.shared-secret:}")
    private String internalAuthSharedSecret;

    @Value("${vendor.delete.refund-hold-days:14}")
    private int vendorDeleteRefundHoldDays;

    @Value("${vendor.audit.outbox.retry-base-delay-seconds:15}")
    private long vendorAuditRetryBaseDelaySeconds;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse create(UpsertVendorRequest request) {
        Vendor vendor = new Vendor();
        applyVendorRequest(vendor, request);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.CREATED, null, null, null, "ADMIN_API", "VENDOR", saved.getId().toString(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public VendorResponse update(UUID id, UpsertVendorRequest request) {
        Vendor vendor = getNonDeletedVendor(id);
        VendorResponse beforeState = toVendorResponse(vendor);
        applyVendorRequest(vendor, request);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.UPDATED, null, null, null, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    public PublicVendorResponse getPublicByIdOrSlug(String idOrSlug) {
        Vendor vendor = findByIdOrSlug(idOrSlug);
        if (!isStorefrontVisible(vendor)) {
            throw new ResourceNotFoundException("Vendor not found: " + idOrSlug);
        }
        return toPublicVendorResponse(vendor);
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
    public Page<PublicVendorResponse> listPublicActive(String category, Pageable pageable) {
        if (StringUtils.hasText(category)) {
            return vendorRepository.findActiveAcceptingVendorsByCategory(VendorStatus.ACTIVE, category.trim(), pageable)
                    .map(this::toPublicVendorResponse);
        }
        return vendorRepository.findByDeletedFalseAndActiveTrueAndAcceptingOrdersTrueAndStatusOrderByNameAsc(VendorStatus.ACTIVE, pageable)
                .map(this::toPublicVendorResponse);
    }

    @Override
    public Page<VendorResponse> listAllNonDeleted(Pageable pageable) {
        return listAllNonDeleted(null, pageable);
    }

    @Override
    public Page<VendorResponse> listAllNonDeleted(String q, Pageable pageable) {
        if (StringUtils.hasText(q)) {
            String normalized = q.trim().toLowerCase(Locale.ROOT);
            return vendorRepository.findByDeletedFalseAndNormalizedNameContainingOrderByNameAsc(normalized, pageable)
                    .map(this::toVendorResponse);
        }
        return vendorRepository.findByDeletedFalseOrderByNameAsc(pageable).map(this::toVendorResponse);
    }

    @Override
    public Page<VendorResponse> listDeleted(Pageable pageable) {
        return vendorRepository.findByDeletedTrueOrderByUpdatedAtDesc(pageable).map(this::toVendorResponse);
    }

    @Override
    public Page<VendorLifecycleAuditResponse> listLifecycleAudit(UUID id, Pageable pageable) {
        vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
        return vendorLifecycleAuditRepository.findByVendorIdOrderByCreatedAtDesc(id, pageable)
                .map(this::toVendorLifecycleAuditResponse);
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
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public void softDelete(UUID id) {
        softDelete(id, null, null, null);
    }

    @Override
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public void softDelete(UUID id, String reason, String actorSub, String actorRoles) {
        // Delegate through self-proxy so @Transactional(NOT_SUPPORTED) on confirmDelete is respected.
        self.confirmDelete(id, reason, actorSub, actorRoles);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse requestDelete(UUID id, String reason, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(id);
        if (vendor.getDeletionRequestedAt() != null) {
            return toVendorResponse(vendor);
        }
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setDeletionRequestedAt(Instant.now());
        vendor.setDeletionRequestReason(trimToNull(reason));
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.DELETE_REQUESTED, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
    public void confirmDelete(UUID id, String reason, String actorSub, String actorRoles) {
        // Stop accepting orders first to prevent new orders during deletion check
        transactionTemplate.executeWithoutResult(status -> {
            Vendor v = vendorRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
            if (!v.isDeleted() && v.isAcceptingOrders()) {
                v.setAcceptingOrders(false);
                vendorRepository.save(v);
            }
        });

        // Check eligibility OUTSIDE transaction (involves HTTP call to order-service)
        VendorDeletionEligibilityResponse eligibility = getDeletionEligibility(id);
        if (!eligibility.eligible()) {
            String blocker = eligibility.blockingReasons().isEmpty()
                    ? "Vendor cannot be deleted yet"
                    : String.join(", ", eligibility.blockingReasons());
            throw new ValidationException("Vendor cannot be deleted: " + blocker);
        }

        // DB mutation inside a short transaction with pessimistic lock to prevent race conditions
        transactionTemplate.executeWithoutResult(status -> {
            Vendor vendor = vendorRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
            if (vendor.isDeleted()) {
                return;
            }
            if (vendor.getDeletionRequestedAt() == null) {
                throw new ValidationException("Delete request must be created before confirm delete");
            }
            VendorResponse beforeState = toVendorResponse(vendor);
            vendor.setDeleted(true);
            vendor.setDeletedAt(Instant.now());
            vendor.setActive(false);
            vendor.setAcceptingOrders(false);
            if (StringUtils.hasText(reason)) {
                vendor.setDeletionRequestReason(trimToNull(reason));
            }
            vendorRepository.save(vendor);
            recordLifecycleAudit(vendor, VendorLifecycleAction.DELETE_CONFIRMED, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", vendor.getId().toString(), beforeState, toVendorResponse(vendor));
        });

        // Deactivate all vendor products in product-service (best-effort)
        try {
            productCatalogAdminClient.deactivateAllByVendor(id, requireInternalAuth());
        } catch (RuntimeException ex) {
            log.warn("Failed to deactivate products for deleted vendor {}. " +
                    "Products may remain active until manual cleanup.", id, ex);
        }

        syncProductCatalogVisibility(id);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
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
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setAcceptingOrders(false);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.STOP_ORDERS, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
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
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setAcceptingOrders(true);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.RESUME_ORDERS, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#id")
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
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setDeleted(false);
        vendor.setDeletedAt(null);
        vendor.setDeletionRequestedAt(null);
        vendor.setDeletionRequestReason(null);
        vendor.setAcceptingOrders(false);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.RESTORED, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
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
    public Page<VendorUserResponse> listVendorUsers(UUID vendorId, Pageable pageable) {
        getNonDeletedVendor(vendorId);
        return vendorUserRepository.findByVendorIdOrderByRoleAscCreatedAtAsc(vendorId, pageable)
                .map(this::toVendorUserResponse);
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
        try {
            VendorUser saved = vendorUserRepository.save(user);
            VendorUserResponse response = toVendorUserResponse(saved);
            recordLifecycleAudit(
                    vendor,
                    VendorLifecycleAction.USER_ADDED,
                    "Vendor membership created",
                    null,
                    null,
                    "ADMIN_API",
                    "VENDOR_USER",
                    saved.getId().toString(),
                    null,
                    response
            );
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException("Vendor user already exists for vendor and keycloakUserId");
        }
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorUserResponse updateVendorUser(UUID vendorId, UUID membershipId, UpsertVendorUserRequest request) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        VendorUser user = vendorUserRepository.findByIdAndVendorId(membershipId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor user membership not found: " + membershipId));
        VendorUserResponse beforeState = toVendorUserResponse(user);
        String incomingKeycloak = normalizeRequired(request.keycloakUserId(), "keycloakUserId", 120);
        if (!incomingKeycloak.equalsIgnoreCase(user.getKeycloakUserId())
                && vendorUserRepository.existsByVendorIdAndKeycloakUserId(vendorId, incomingKeycloak)) {
            throw new ValidationException("Another vendor user already exists with this keycloakUserId");
        }
        applyVendorUserRequest(user, request);
        try {
            VendorUser saved = vendorUserRepository.save(user);
            VendorUserResponse response = toVendorUserResponse(saved);
            recordLifecycleAudit(
                    vendor,
                    VendorLifecycleAction.USER_UPDATED,
                    "Vendor membership updated",
                    null,
                    null,
                    "ADMIN_API",
                    "VENDOR_USER",
                    saved.getId().toString(),
                    beforeState,
                    response
            );
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationException("Another vendor user already exists with this keycloakUserId");
        }
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void removeVendorUser(UUID vendorId, UUID membershipId) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        VendorUser user = vendorUserRepository.findByIdAndVendorId(membershipId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor user membership not found: " + membershipId));
        VendorUserResponse beforeState = toVendorUserResponse(user);
        vendorUserRepository.delete(user);
        recordLifecycleAudit(
                vendor,
                VendorLifecycleAction.USER_REMOVED,
                "Vendor membership removed",
                null,
                null,
                "ADMIN_API",
                "VENDOR_USER",
                membershipId.toString(),
                beforeState,
                Map.of("removed", true, "vendorId", vendorId, "membershipId", membershipId)
        );
    }

    @Override
    public List<VendorAccessMembershipResponse> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        return vendorUserRepository
                .findAccessibleMembershipsByKeycloakUser(normalized)
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
        return vendorIds.stream()
                .distinct()
                .map(self::getOperationalState)
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
        vendor.setWebsiteUrl(trimToNull(request.websiteUrl()));
        vendor.setDescription(trimToNull(request.description()));
        vendor.setCommissionRate(request.commissionRate());
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

        // Gap 51: Policies
        vendor.setReturnPolicy(trimToNull(request.returnPolicy()));
        vendor.setShippingPolicy(trimToNull(request.shippingPolicy()));
        if (request.processingTimeDays() != null) {
            vendor.setProcessingTimeDays(request.processingTimeDays());
        }
        if (request.acceptsReturns() != null) {
            vendor.setAcceptsReturns(request.acceptsReturns());
        }
        if (request.returnWindowDays() != null) {
            vendor.setReturnWindowDays(request.returnWindowDays());
        }
        vendor.setFreeShippingThreshold(request.freeShippingThreshold());

        // Gap 52: Categories
        vendor.setPrimaryCategory(trimToNull(request.primaryCategory()));
        if (request.specializations() != null) {
            vendor.getSpecializations().clear();
            vendor.getSpecializations().addAll(request.specializations());
        }
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

    private PublicVendorResponse toPublicVendorResponse(Vendor v) {
        return new PublicVendorResponse(
                v.getId(),
                v.getName(),
                v.getSlug(),
                v.getSupportEmail(),
                v.getLogoImage(),
                v.getBannerImage(),
                v.getWebsiteUrl(),
                v.getDescription(),
                v.isAcceptingOrders(),
                v.getAverageRating(),
                v.getTotalRatings(),
                v.getTotalOrdersCompleted(),
                v.getReturnPolicy(),
                v.getShippingPolicy(),
                v.getProcessingTimeDays(),
                v.isAcceptsReturns(),
                v.getReturnWindowDays(),
                v.getFreeShippingThreshold(),
                v.getPrimaryCategory(),
                v.getSpecializations() != null ? Set.copyOf(v.getSpecializations()) : Set.of(),
                v.getCreatedAt()
        );
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
                // Gap 49: Verification
                v.getVerificationStatus(),
                v.isVerified(),
                v.getVerifiedAt(),
                v.getVerificationRequestedAt(),
                v.getVerificationNotes(),
                v.getVerificationDocumentUrl(),
                // Gap 50: Metrics
                v.getAverageRating(),
                v.getTotalRatings(),
                v.getFulfillmentRate(),
                v.getDisputeRate(),
                v.getResponseTimeHours(),
                v.getTotalOrdersCompleted(),
                // Gap 51: Policies
                v.getReturnPolicy(),
                v.getShippingPolicy(),
                v.getProcessingTimeDays(),
                v.isAcceptsReturns(),
                v.getReturnWindowDays(),
                v.getFreeShippingThreshold(),
                // Gap 52: Categories
                v.getPrimaryCategory(),
                v.getSpecializations() != null ? Set.copyOf(v.getSpecializations()) : Set.of(),
                // Existing
                v.isDeleted(),
                v.getDeletedAt(),
                v.getDeletionRequestedAt(),
                v.getDeletionRequestReason(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                v.getCommissionRate()
        );
    }

    private VendorLifecycleAuditResponse toVendorLifecycleAuditResponse(VendorLifecycleAudit audit) {
        return new VendorLifecycleAuditResponse(
                audit.getId(),
                audit.getVendor().getId(),
                audit.getAction(),
                audit.getResourceType(),
                audit.getResourceId(),
                audit.getActorSub(),
                audit.getActorTenantId(),
                audit.getActorRoles(),
                audit.getActorType(),
                audit.getChangeSource(),
                audit.getReason(),
                audit.getChangeSet(),
                audit.getClientIp(),
                audit.getUserAgent(),
                audit.getRequestId(),
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
                isStorefrontVisible(vendor),
                vendor.isVerified()
        );
    }

    private boolean isStorefrontVisible(Vendor vendor) {
        return vendor != null
                && !vendor.isDeleted()
                && vendor.isActive()
                && vendor.getStatus() == VendorStatus.ACTIVE
                && vendor.isAcceptingOrders()
                && vendor.isVerified();
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
        Runnable syncTask = () -> {
            try {
                productCatalogAdminClient.evictPublicCachesForVendor(vendorId, requireInternalAuth());
            } catch (RuntimeException ex) {
                log.warn("Failed to sync product catalog visibility for vendor {}. " +
                        "Stale cache entries may persist until TTL expiry.", vendorId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncTask.run();
                }
            });
            return;
        }
        syncTask.run();
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void processAuditOutboxBatch(int batchSize) {
        List<VendorAuditOutboxEvent> events = vendorAuditOutboxRepository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );
        for (VendorAuditOutboxEvent event : events) {
            processAuditOutboxEvent(event.getId());
        }
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void processAuditOutboxEvent(UUID eventId) {
        VendorAuditOutboxEvent event = vendorAuditOutboxRepository.findById(eventId).orElse(null);
        if (event == null || event.getProcessedAt() != null) {
            return;
        }
        try {
            if (!vendorLifecycleAuditRepository.existsBySourceEventId(event.getId())) {
                Vendor vendor = vendorRepository.findById(event.getVendorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Vendor not found for audit event: " + event.getVendorId()));
                vendorLifecycleAuditRepository.save(VendorLifecycleAudit.builder()
                        .sourceEventId(event.getId())
                        .vendor(vendor)
                        .action(event.getAction())
                        .resourceType(defaultValue(event.getResourceType(), "VENDOR"))
                        .resourceId(trimToNull(event.getResourceId()))
                        .actorSub(defaultValue(event.getActorSub(), "system"))
                        .actorTenantId(trimToNull(event.getActorTenantId()))
                        .actorRoles(trimToNull(event.getActorRoles()))
                        .actorType(defaultValue(event.getActorType(), "SYSTEM"))
                        .changeSource(defaultValue(event.getChangeSource(), "SYSTEM"))
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
            vendorAuditOutboxRepository.save(event);
            log.warn("Vendor audit outbox event {} failed on attempt {}", event.getId(), event.getAttemptCount(), ex);
        }
    }

    private void recordLifecycleAudit(Vendor vendor, VendorLifecycleAction action, String reason, String actorSub, String actorRoles) {
        recordLifecycleAudit(vendor, action, reason, actorSub, actorRoles, "ADMIN_API", "VENDOR", vendor == null ? null : vendor.getId().toString(), null, null);
    }

    private void recordLifecycleAudit(
            Vendor vendor,
            VendorLifecycleAction action,
            String reason,
            String actorSub,
            String actorRoles,
            String changeSource,
            String resourceType,
            String resourceId,
            Object beforeState,
            Object afterState
    ) {
        if (vendor == null || action == null || vendor.getId() == null) {
            return;
        }
        VendorAuditRequestContext context = vendorAuditRequestContextResolver.resolve(actorSub, actorRoles, changeSource);
        vendorAuditOutboxRepository.save(VendorAuditOutboxEvent.builder()
                .vendorId(vendor.getId())
                .action(action)
                .resourceType(defaultValue(resourceType, "VENDOR"))
                .resourceId(trimToNull(resourceId))
                .actorSub(defaultValue(context.actorSub(), "system"))
                .actorTenantId(trimToNull(context.actorTenantId()))
                .actorRoles(trimToNull(context.actorRoles()))
                .actorType(defaultValue(context.actorType(), "SYSTEM"))
                .changeSource(defaultValue(context.changeSource(), "SYSTEM"))
                .reason(vendorAuditPayloadSanitizer.sanitizeReason(reason))
                .changeSet(vendorAuditPayloadSanitizer.buildChangeSet(beforeState, afterState))
                .clientIp(trimToNull(context.clientIp()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    private void markAuditOutboxProcessed(VendorAuditOutboxEvent event) {
        event.setProcessedAt(Instant.now());
        event.setLastError(null);
        vendorAuditOutboxRepository.save(event);
    }

    private long resolveAuditRetryDelaySeconds(int attemptCount) {
        long base = Math.max(5L, vendorAuditRetryBaseDelaySeconds);
        long multiplier = Math.max(1L, attemptCount);
        return Math.min(900L, base * multiplier * multiplier);
    }

    private Vendor resolveVendorForKeycloakUser(String keycloakUserId, UUID vendorIdHint) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        List<VendorUser> memberships = vendorUserRepository
                .findAccessibleMembershipsByKeycloakUser(normalized);
        if (memberships.isEmpty()) {
            throw new ResourceNotFoundException("No active vendor membership found for user");
        }
        if (memberships.size() == 1) {
            return memberships.get(0).getVendor();
        }
        if (vendorIdHint == null) {
            throw new ValidationException("Multiple vendor memberships found. Specify vendorId query parameter.");
        }
        return memberships.stream()
                .filter(m -> vendorIdHint.equals(m.getVendor().getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No active vendor membership found for vendorId: " + vendorIdHint))
                .getVendor();
    }

    private Vendor resolveVendorOwner(String keycloakUserId, UUID vendorIdHint) {
        String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
        List<VendorUser> memberships = vendorUserRepository
                .findAccessibleMembershipsByKeycloakUser(normalized);
        if (memberships.isEmpty()) {
            throw new ResourceNotFoundException("No active vendor membership found for user");
        }

        VendorUser membership;
        if (memberships.size() == 1) {
            membership = memberships.get(0);
        } else {
            if (vendorIdHint == null) {
                throw new ValidationException("Multiple vendor memberships found. Specify vendorId query parameter.");
            }
            membership = memberships.stream()
                    .filter(m -> vendorIdHint.equals(m.getVendor().getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active vendor membership found for vendorId: " + vendorIdHint));
        }

        if (membership.getRole() != VendorUserRole.OWNER) {
            throw new UnauthorizedException("Only vendor owners can perform this operation");
        }
        return membership.getVendor();
    }

    @Override
    public VendorResponse getVendorForKeycloakUser(String keycloakUserId, UUID vendorIdHint) {
        return toVendorResponse(resolveVendorForKeycloakUser(keycloakUserId, vendorIdHint));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#result.id()")
    public VendorResponse updateVendorSelfService(String keycloakUserId, UUID vendorIdHint, UpdateVendorSelfServiceRequest request) {
        Vendor vendor = resolveVendorForKeycloakUser(keycloakUserId, vendorIdHint);
        VendorResponse beforeState = toVendorResponse(vendor);
        applySelfServiceRequest(vendor, request);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.UPDATED, "Vendor storefront settings updated", keycloakUserId, null, "VENDOR_SELF_SERVICE", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#result.id()")
    public VendorResponse selfServiceStopOrders(String keycloakUserId, UUID vendorIdHint) {
        Vendor vendor = resolveVendorForKeycloakUser(keycloakUserId, vendorIdHint);
        if (!vendor.isAcceptingOrders()) {
            return toVendorResponse(vendor);
        }
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setAcceptingOrders(false);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.STOP_ORDERS, "Vendor stopped accepting orders", keycloakUserId, null, "VENDOR_SELF_SERVICE", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#result.id()")
    public VendorResponse selfServiceResumeOrders(String keycloakUserId, UUID vendorIdHint) {
        Vendor vendor = resolveVendorForKeycloakUser(keycloakUserId, vendorIdHint);
        if (!vendor.isActive()) {
            throw new ValidationException("Cannot resume orders for an inactive vendor");
        }
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new ValidationException("Cannot resume orders unless vendor status is ACTIVE");
        }
        if (vendor.isAcceptingOrders()) {
            return toVendorResponse(vendor);
        }
        VendorResponse beforeState = toVendorResponse(vendor);
        vendor.setAcceptingOrders(true);
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.RESUME_ORDERS, "Vendor resumed accepting orders", keycloakUserId, null, "VENDOR_SELF_SERVICE", "VENDOR", saved.getId().toString(), beforeState, response);
        syncProductCatalogVisibility(saved.getId());
        return response;
    }

    @Override
    public VendorPayoutConfigResponse getPayoutConfig(String keycloakUserId, UUID vendorIdHint) {
        Vendor vendor = resolveVendorOwner(keycloakUserId, vendorIdHint);
        VendorPayoutConfig config = vendorPayoutConfigRepository.findByVendorId(vendor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payout config not found for vendor: " + vendor.getId()));
        return toPayoutConfigResponse(config);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorPayoutConfigResponse upsertPayoutConfig(String keycloakUserId, UUID vendorIdHint, UpsertVendorPayoutConfigRequest request) {
        Vendor vendor = resolveVendorOwner(keycloakUserId, vendorIdHint);
        VendorPayoutConfig config = vendorPayoutConfigRepository.findByVendorId(vendor.getId())
                .orElseGet(() -> {
                    VendorPayoutConfig c = new VendorPayoutConfig();
                    c.setVendor(vendor);
                    return c;
                });
        VendorPayoutConfigResponse beforeState = config.getId() == null ? null : toPayoutConfigResponse(config);
        applyPayoutConfigRequest(config, request);
        VendorPayoutConfig saved = vendorPayoutConfigRepository.save(config);
        VendorPayoutConfigResponse response = toPayoutConfigResponse(saved);
        recordLifecycleAudit(
                vendor,
                VendorLifecycleAction.PAYOUT_CONFIG_UPDATED,
                "Vendor payout configuration updated",
                keycloakUserId,
                null,
                "VENDOR_SELF_SERVICE",
                "VENDOR_PAYOUT_CONFIG",
                saved.getId() == null ? vendor.getId().toString() : saved.getId().toString(),
                beforeState,
                response
        );
        return response;
    }

    private void applySelfServiceRequest(Vendor vendor, UpdateVendorSelfServiceRequest request) {
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
        vendor.setWebsiteUrl(trimToNull(request.websiteUrl()));
        vendor.setDescription(trimToNull(request.description()));

        // Gap 51: Policies
        vendor.setReturnPolicy(trimToNull(request.returnPolicy()));
        vendor.setShippingPolicy(trimToNull(request.shippingPolicy()));
        if (request.processingTimeDays() != null) {
            vendor.setProcessingTimeDays(request.processingTimeDays());
        }
        if (request.acceptsReturns() != null) {
            vendor.setAcceptsReturns(request.acceptsReturns());
        }
        if (request.returnWindowDays() != null) {
            vendor.setReturnWindowDays(request.returnWindowDays());
        }
        vendor.setFreeShippingThreshold(request.freeShippingThreshold());

        // Gap 52: Categories
        vendor.setPrimaryCategory(trimToNull(request.primaryCategory()));
        if (request.specializations() != null) {
            vendor.getSpecializations().clear();
            vendor.getSpecializations().addAll(request.specializations());
        }
    }

    private void applyPayoutConfigRequest(VendorPayoutConfig config, UpsertVendorPayoutConfigRequest request) {
        config.setPayoutCurrency(normalizeRequired(request.payoutCurrency(), "payoutCurrency", 3).toUpperCase(Locale.ROOT));
        config.setPayoutSchedule(Objects.requireNonNull(request.payoutSchedule(), "payoutSchedule"));
        config.setPayoutMinimum(request.payoutMinimum());
        config.setBankAccountHolder(trimToNull(request.bankAccountHolder()));
        config.setBankName(trimToNull(request.bankName()));
        config.setBankRoutingCode(trimToNull(request.bankRoutingCode()));
        config.setBankAccountNumberMasked(trimToNull(request.bankAccountNumberMasked()));
        config.setTaxId(trimToNull(request.taxId()));
    }

    private VendorPayoutConfigResponse toPayoutConfigResponse(VendorPayoutConfig c) {
        return new VendorPayoutConfigResponse(
                c.getId(),
                c.getVendor().getId(),
                c.getPayoutCurrency(),
                c.getPayoutSchedule(),
                c.getPayoutMinimum(),
                c.getBankAccountHolder(),
                c.getBankName(),
                c.getBankRoutingCode(),
                c.getBankAccountNumberMasked(),
                c.getTaxId(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    // --- Gap 49: Verification workflow ---

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse requestVerification(String keycloakUserId, UUID vendorIdHint, RequestVerificationRequest request) {
        Vendor vendor = resolveVendorForKeycloakUser(keycloakUserId, vendorIdHint);
        VendorResponse beforeState = toVendorResponse(vendor);
        VerificationStatus current = vendor.getVerificationStatus();
        if (current != VerificationStatus.UNVERIFIED && current != VerificationStatus.VERIFICATION_REJECTED) {
            throw new ValidationException("Verification can only be requested when status is UNVERIFIED or VERIFICATION_REJECTED, current: " + current);
        }
        vendor.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        vendor.setVerificationRequestedAt(Instant.now());
        if (request != null) {
            vendor.setVerificationDocumentUrl(trimToNull(request.verificationDocumentUrl()));
            vendor.setVerificationNotes(trimToNull(request.notes()));
        }
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.VERIFICATION_REQUESTED, "Vendor verification requested", keycloakUserId, null, "VENDOR_SELF_SERVICE", "VENDOR", saved.getId().toString(), beforeState, response);
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#vendorId")
    public VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        VendorResponse beforeState = toVendorResponse(vendor);
        if (vendor.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new ValidationException("Can only approve verification when status is PENDING_VERIFICATION, current: " + vendor.getVerificationStatus());
        }
        vendor.setVerificationStatus(VerificationStatus.VERIFIED);
        vendor.setVerified(true);
        vendor.setVerifiedAt(Instant.now());
        if (request != null && StringUtils.hasText(request.notes())) {
            vendor.setVerificationNotes(request.notes().trim());
        }
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.VERIFICATION_APPROVED,
                request != null ? request.notes() : null, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        return response;
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "vendorOperationalState", key = "#vendorId")
    public VendorResponse rejectVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        VendorResponse beforeState = toVendorResponse(vendor);
        if (vendor.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new ValidationException("Can only reject verification when status is PENDING_VERIFICATION, current: " + vendor.getVerificationStatus());
        }
        vendor.setVerificationStatus(VerificationStatus.VERIFICATION_REJECTED);
        vendor.setVerified(false);
        if (request != null && StringUtils.hasText(request.notes())) {
            vendor.setVerificationNotes(request.notes().trim());
        }
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.VERIFICATION_REJECTED,
                request != null ? request.notes() : null, actorSub, actorRoles, "ADMIN_API", "VENDOR", saved.getId().toString(), beforeState, response);
        return response;
    }

    // --- Gap 50: Performance metrics ---

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse updateMetrics(UUID vendorId, UpdateVendorMetricsRequest request) {
        Vendor vendor = getNonDeletedVendor(vendorId);
        VendorResponse beforeState = toVendorResponse(vendor);
        if (request.averageRating() != null) {
            vendor.setAverageRating(request.averageRating());
        }
        if (request.totalRatings() != null) {
            vendor.setTotalRatings(request.totalRatings());
        }
        if (request.fulfillmentRate() != null) {
            vendor.setFulfillmentRate(request.fulfillmentRate());
        }
        if (request.disputeRate() != null) {
            vendor.setDisputeRate(request.disputeRate());
        }
        if (request.responseTimeHours() != null) {
            vendor.setResponseTimeHours(request.responseTimeHours());
        }
        if (request.totalOrdersCompleted() != null) {
            vendor.setTotalOrdersCompleted(request.totalOrdersCompleted());
        }
        Vendor saved = vendorRepository.save(vendor);
        VendorResponse response = toVendorResponse(saved);
        recordLifecycleAudit(saved, VendorLifecycleAction.UPDATED, "Vendor metrics synchronized", null, null, "SYSTEM_SYNC", "VENDOR", saved.getId().toString(), beforeState, response);
        return response;
    }

    @Override
    public Map<UUID, String> getVendorNames(List<UUID> vendorIds) {
        if (vendorIds == null || vendorIds.isEmpty()) return Map.of();
        return vendorRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName));
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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
