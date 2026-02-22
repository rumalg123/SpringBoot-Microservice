package com.rumal.poster_service.service;

import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.entity.Poster;
import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.exception.ResourceNotFoundException;
import com.rumal.poster_service.exception.ValidationException;
import com.rumal.poster_service.repo.PosterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PosterServiceImpl implements PosterService {

    private final PosterRepository posterRepository;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "postersByPlacement", allEntries = true),
            @CacheEvict(cacheNames = "posterById", allEntries = true)
    })
    public PosterResponse create(UpsertPosterRequest request) {
        Poster poster = new Poster();
        applyRequest(poster, request);
        return toResponse(posterRepository.save(poster));
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "postersByPlacement", allEntries = true),
            @CacheEvict(cacheNames = "posterById", allEntries = true)
    })
    public PosterResponse update(UUID id, UpsertPosterRequest request) {
        Poster poster = getActiveEntity(id);
        applyRequest(poster, request);
        return toResponse(posterRepository.save(poster));
    }

    @Override
    @Cacheable(cacheNames = "posterById", key = "'any::' + #idOrSlug")
    public PosterResponse getByIdOrSlug(String idOrSlug) {
        UUID parsed = tryParseUuid(idOrSlug);
        Poster poster;
        if (parsed != null) {
            poster = posterRepository.findById(parsed)
                    .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + idOrSlug));
        } else {
            String normalizedSlug = normalizeRequestedSlug(idOrSlug);
            poster = posterRepository.findBySlug(normalizedSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + idOrSlug));
        }
        if (poster.isDeleted()) {
            throw new ResourceNotFoundException("Poster not found: " + idOrSlug);
        }
        Instant now = Instant.now();
        if (!poster.isActive() || !isActiveInWindow(poster, now)) {
            throw new ResourceNotFoundException("Poster not found: " + idOrSlug);
        }
        return toResponse(poster);
    }

    @Override
    @Cacheable(cacheNames = "postersByPlacement", key = "'placement::' + #placement")
    public List<PosterResponse> listActiveByPlacement(PosterPlacement placement) {
        Instant now = Instant.now();
        return posterRepository.findByDeletedFalseAndPlacementOrderBySortOrderAscCreatedAtDesc(placement)
                .stream()
                .filter(Poster::isActive)
                .filter(p -> isActiveInWindow(p, now))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = "postersByPlacement", key = "'all-active'")
    public List<PosterResponse> listAllActive() {
        Instant now = Instant.now();
        return posterRepository.findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc()
                .stream()
                .filter(Poster::isActive)
                .filter(p -> isActiveInWindow(p, now))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PosterResponse> listAllNonDeleted() {
        return posterRepository.findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PosterResponse> listDeleted() {
        return posterRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "postersByPlacement", allEntries = true),
            @CacheEvict(cacheNames = "posterById", allEntries = true)
    })
    public void softDelete(UUID id) {
        Poster poster = getActiveEntity(id);
        poster.setDeleted(true);
        poster.setDeletedAt(Instant.now());
        poster.setActive(false);
        posterRepository.save(poster);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @Caching(evict = {
            @CacheEvict(cacheNames = "postersByPlacement", allEntries = true),
            @CacheEvict(cacheNames = "posterById", allEntries = true)
    })
    public PosterResponse restore(UUID id) {
        Poster poster = posterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + id));
        if (!poster.isDeleted()) {
            throw new ValidationException("Poster is not soft deleted: " + id);
        }
        poster.setDeleted(false);
        poster.setDeletedAt(null);
        return toResponse(posterRepository.save(poster));
    }

    @Override
    public boolean isSlugAvailable(String slug, UUID excludeId) {
        String normalizedSlug = normalizeRequestedSlug(slug);
        if (!StringUtils.hasText(normalizedSlug)) {
            return false;
        }
        return excludeId == null
                ? !posterRepository.existsBySlug(normalizedSlug)
                : !posterRepository.existsBySlugAndIdNot(normalizedSlug, excludeId);
    }

    private void applyRequest(Poster poster, UpsertPosterRequest request) {
        validateDateWindow(request.startAt(), request.endAt());
        validateLink(request.linkType(), request.linkTarget());
        String name = request.name().trim();
        String requestedSlug = normalizeRequestedSlug(request.slug());
        boolean autoSlug = !StringUtils.hasText(requestedSlug);
        String baseSlug = autoSlug ? SlugUtils.toSlug(name) : requestedSlug;
        String resolvedSlug = resolveUniqueSlug(baseSlug, poster.getId(), autoSlug);

        poster.setName(name);
        poster.setSlug(resolvedSlug);
        poster.setPlacement(request.placement());
        poster.setSize(request.size());
        poster.setDesktopImage(normalizeImageKey(request.desktopImage(), "desktopImage"));
        poster.setMobileImage(StringUtils.hasText(request.mobileImage()) ? normalizeImageKey(request.mobileImage(), "mobileImage") : null);
        poster.setLinkType(request.linkType());
        poster.setLinkTarget(StringUtils.hasText(request.linkTarget()) ? request.linkTarget().trim() : null);
        poster.setOpenInNewTab(Boolean.TRUE.equals(request.openInNewTab()));
        poster.setTitle(trimToNull(request.title()));
        poster.setSubtitle(trimToNull(request.subtitle()));
        poster.setCtaLabel(trimToNull(request.ctaLabel()));
        poster.setBackgroundColor(trimToNull(request.backgroundColor()));
        poster.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        poster.setActive(request.active() == null || request.active());
        poster.setStartAt(request.startAt());
        poster.setEndAt(request.endAt());
        poster.setDeleted(false);
        poster.setDeletedAt(null);
    }

    private void validateDateWindow(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new ValidationException("endAt cannot be before startAt");
        }
    }

    private void validateLink(PosterLinkType linkType, String linkTarget) {
        if (linkType == PosterLinkType.NONE) {
            return;
        }
        if (!StringUtils.hasText(linkTarget)) {
            throw new ValidationException("linkTarget is required when linkType is not NONE");
        }
    }

    private boolean isActiveInWindow(Poster p, Instant now) {
        if (p.getStartAt() != null && now.isBefore(p.getStartAt())) {
            return false;
        }
        return p.getEndAt() == null || !now.isAfter(p.getEndAt());
    }

    private Poster getActiveEntity(UUID id) {
        return posterRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + id));
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

    private String normalizeRequestedSlug(String slug) {
        String normalized = SlugUtils.toSlug(slug);
        return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
    }

    private String resolveUniqueSlug(String baseSlug, UUID existingId, boolean allowAutoSuffix) {
        String seed = StringUtils.hasText(baseSlug) ? baseSlug : "poster";
        if (isSlugAvailable(seed, existingId)) {
            return seed;
        }
        if (!allowAutoSuffix) {
            throw new ValidationException("Poster slug must be unique: " + seed);
        }
        int suffix = 2;
        while (suffix < 100_000) {
            String candidate = appendSlugSuffix(seed, suffix, 180);
            if (isSlugAvailable(candidate, existingId)) {
                return candidate;
            }
            suffix++;
        }
        throw new ValidationException("Unable to generate a unique poster slug");
    }

    private String appendSlugSuffix(String slug, int suffix, int maxLen) {
        String suffixPart = "-" + suffix;
        int allowed = Math.max(1, maxLen - suffixPart.length());
        String base = slug.length() > allowed ? slug.substring(0, allowed) : slug;
        return base + suffixPart;
    }

    private String normalizeImageKey(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ValidationException(fieldName + " is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^(posters/)?[a-z0-9-]+\\.(jpg|jpeg|png|webp)$")) {
            throw new ValidationException("Invalid image key format for " + fieldName);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PosterResponse toResponse(Poster p) {
        return new PosterResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getPlacement(),
                p.getSize(),
                p.getDesktopImage(),
                p.getMobileImage(),
                p.getLinkType(),
                p.getLinkTarget(),
                p.isOpenInNewTab(),
                p.getTitle(),
                p.getSubtitle(),
                p.getCtaLabel(),
                p.getBackgroundColor(),
                p.getSortOrder(),
                p.isActive(),
                p.getStartAt(),
                p.getEndAt(),
                p.isDeleted(),
                p.getDeletedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
