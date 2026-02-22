package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.entity.VendorUser;
import com.rumal.vendor_service.exception.ResourceNotFoundException;
import com.rumal.vendor_service.exception.ValidationException;
import com.rumal.vendor_service.repo.VendorRepository;
import com.rumal.vendor_service.repo.VendorUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final VendorUserRepository vendorUserRepository;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse create(UpsertVendorRequest request) {
        Vendor vendor = new Vendor();
        applyVendorRequest(vendor, request);
        return toVendorResponse(vendorRepository.save(vendor));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse update(UUID id, UpsertVendorRequest request) {
        Vendor vendor = getNonDeletedVendor(id);
        applyVendorRequest(vendor, request);
        return toVendorResponse(vendorRepository.save(vendor));
    }

    @Override
    public VendorResponse getByIdOrSlug(String idOrSlug) {
        Vendor vendor = findByIdOrSlug(idOrSlug);
        if (vendor.isDeleted() || !vendor.isActive() || vendor.getStatus() != VendorStatus.ACTIVE) {
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
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDelete(UUID id) {
        Vendor vendor = getNonDeletedVendor(id);
        vendor.setDeleted(true);
        vendor.setDeletedAt(java.time.Instant.now());
        vendor.setActive(false);
        vendorRepository.save(vendor);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public VendorResponse restore(UUID id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
        if (!vendor.isDeleted()) {
            throw new ValidationException("Vendor is not soft deleted: " + id);
        }
        vendor.setDeleted(false);
        vendor.setDeletedAt(null);
        return toVendorResponse(vendorRepository.save(vendor));
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
        vendor.setStatus(request.status());
        vendor.setActive(request.active() == null || request.active());
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
                v.isDeleted(),
                v.getDeletedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt()
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
}
