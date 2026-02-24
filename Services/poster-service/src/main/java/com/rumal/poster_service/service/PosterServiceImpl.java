package com.rumal.poster_service.service;

import com.rumal.poster_service.dto.PosterAnalyticsResponse;
import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.PosterVariantResponse;
import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.dto.UpsertPosterVariantRequest;
import com.rumal.poster_service.entity.Poster;
import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.entity.PosterVariant;
import com.rumal.poster_service.exception.ResourceNotFoundException;
import com.rumal.poster_service.exception.ValidationException;
import com.rumal.poster_service.repo.PosterRepository;
import com.rumal.poster_service.repo.PosterVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class PosterServiceImpl implements PosterService {

    private final PosterRepository posterRepository;
    private final PosterVariantRepository posterVariantRepository;
    private final CacheManager cacheManager;

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PosterResponse create(UpsertPosterRequest request) {
        Poster poster = new Poster();
        applyRequest(poster, request);
        Poster saved = posterRepository.save(poster);
        evictPosterCaches(saved, null, null);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PosterResponse update(UUID id, UpsertPosterRequest request) {
        Poster poster = getActiveEntity(id);
        PosterPlacement previousPlacement = poster.getPlacement();
        String previousSlug = poster.getSlug();
        applyRequest(poster, request);
        Poster saved = posterRepository.save(poster);
        evictPosterCaches(saved, previousPlacement, previousSlug);
        return toResponse(saved);
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
    public Page<PosterResponse> listActiveByPlacement(PosterPlacement placement, Pageable pageable) {
        return posterRepository.findByDeletedFalseAndPlacementOrderBySortOrderAscCreatedAtDesc(placement, pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<PosterResponse> listAllActive(Pageable pageable) {
        return posterRepository.findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Override
    public List<PosterResponse> listAllNonDeleted() {
        return posterRepository.findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<PosterResponse> listAllNonDeleted(Pageable pageable) {
        return posterRepository.findByDeletedFalseOrderByPlacementAscSortOrderAscCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Override
    public List<PosterResponse> listDeleted() {
        return posterRepository.findByDeletedTrueOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    public Page<PosterResponse> listDeleted(Pageable pageable) {
        return posterRepository.findByDeletedTrueOrderByUpdatedAtDesc(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void softDelete(UUID id) {
        Poster poster = getActiveEntity(id);
        poster.setDeleted(true);
        poster.setDeletedAt(Instant.now());
        poster.setActive(false);
        Poster saved = posterRepository.save(poster);
        evictPosterCaches(saved, saved.getPlacement(), saved.getSlug());
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PosterResponse restore(UUID id) {
        Poster poster = posterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + id));
        if (!poster.isDeleted()) {
            throw new ValidationException("Poster is not soft deleted: " + id);
        }
        poster.setDeleted(false);
        poster.setDeletedAt(null);
        Poster saved = posterRepository.save(poster);
        evictPosterCaches(saved, saved.getPlacement(), saved.getSlug());
        return toResponse(saved);
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
        poster.setTabletImage(StringUtils.hasText(request.tabletImage()) ? normalizeImageKey(request.tabletImage(), "tabletImage") : null);
        poster.setSrcsetDesktop(trimToNull(request.srcsetDesktop()));
        poster.setSrcsetMobile(trimToNull(request.srcsetMobile()));
        poster.setSrcsetTablet(trimToNull(request.srcsetTablet()));
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
        poster.setTargetCountries(request.targetCountries() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(request.targetCountries()));
        poster.setTargetCustomerSegment(trimToNull(request.targetCustomerSegment()));
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
        List<PosterVariant> activeVariants = posterVariantRepository
                .findByPosterIdAndActiveTrueOrderByCreatedAtAsc(p.getId());
        PosterVariantResponse selectedVariant = selectWeightedVariant(activeVariants);

        // If a variant is selected, its images override poster-level images
        String effectiveDesktopImage = p.getDesktopImage();
        String effectiveMobileImage = p.getMobileImage();
        String effectiveTabletImage = p.getTabletImage();
        String effectiveSrcsetDesktop = p.getSrcsetDesktop();
        String effectiveSrcsetMobile = p.getSrcsetMobile();
        String effectiveSrcsetTablet = p.getSrcsetTablet();

        if (selectedVariant != null) {
            if (StringUtils.hasText(selectedVariant.desktopImage())) {
                effectiveDesktopImage = selectedVariant.desktopImage();
            }
            if (StringUtils.hasText(selectedVariant.mobileImage())) {
                effectiveMobileImage = selectedVariant.mobileImage();
            }
            if (StringUtils.hasText(selectedVariant.tabletImage())) {
                effectiveTabletImage = selectedVariant.tabletImage();
            }
            if (StringUtils.hasText(selectedVariant.srcsetDesktop())) {
                effectiveSrcsetDesktop = selectedVariant.srcsetDesktop();
            }
            if (StringUtils.hasText(selectedVariant.srcsetMobile())) {
                effectiveSrcsetMobile = selectedVariant.srcsetMobile();
            }
            if (StringUtils.hasText(selectedVariant.srcsetTablet())) {
                effectiveSrcsetTablet = selectedVariant.srcsetTablet();
            }
        }

        List<PosterVariantResponse> allVariants = posterVariantRepository
                .findByPosterIdOrderByCreatedAtAsc(p.getId())
                .stream()
                .map(this::toVariantResponse)
                .toList();

        return new PosterResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getPlacement(),
                p.getSize(),
                effectiveDesktopImage,
                effectiveMobileImage,
                effectiveTabletImage,
                effectiveSrcsetDesktop,
                effectiveSrcsetMobile,
                effectiveSrcsetTablet,
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
                p.getClickCount(),
                p.getImpressionCount(),
                p.getLastClickAt(),
                p.getLastImpressionAt(),
                p.getTargetCountries() == null ? Set.of() : Set.copyOf(p.getTargetCountries()),
                p.getTargetCustomerSegment(),
                p.isDeleted(),
                p.getDeletedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                selectedVariant,
                allVariants
        );
    }

    private PosterVariantResponse selectWeightedVariant(List<PosterVariant> activeVariants) {
        if (activeVariants == null || activeVariants.isEmpty()) {
            return null;
        }
        int totalWeight = activeVariants.stream().mapToInt(PosterVariant::getWeight).sum();
        if (totalWeight <= 0) {
            return toVariantResponse(activeVariants.getFirst());
        }
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (PosterVariant variant : activeVariants) {
            cumulative += variant.getWeight();
            if (random < cumulative) {
                return toVariantResponse(variant);
            }
        }
        return toVariantResponse(activeVariants.getLast());
    }

    private PosterVariantResponse toVariantResponse(PosterVariant v) {
        double ctr = v.getImpressions() > 0
                ? (double) v.getClicks() / v.getImpressions()
                : 0.0;
        return new PosterVariantResponse(
                v.getId(),
                v.getPoster().getId(),
                v.getVariantName(),
                v.getWeight(),
                v.getDesktopImage(),
                v.getMobileImage(),
                v.getTabletImage(),
                v.getSrcsetDesktop(),
                v.getSrcsetMobile(),
                v.getSrcsetTablet(),
                v.getLinkUrl(),
                v.getImpressions(),
                v.getClicks(),
                ctr,
                v.isActive(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = false, timeout = 5)
    public void recordClick(UUID id) {
        posterRepository.incrementClickCount(id, Instant.now());
    }

    @Override
    @Transactional(readOnly = false, timeout = 5)
    public void recordImpression(UUID id) {
        posterRepository.incrementImpressionCount(id, Instant.now());
    }

    @Override
    public PosterAnalyticsResponse getAnalytics(UUID id) {
        Poster poster = posterRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + id));
        return toAnalyticsResponse(poster);
    }

    @Override
    public List<PosterAnalyticsResponse> listAnalytics() {
        return posterRepository.findByDeletedFalseOrderByClickCountDesc().stream()
                .map(this::toAnalyticsResponse)
                .toList();
    }

    private PosterAnalyticsResponse toAnalyticsResponse(Poster p) {
        double ctr = p.getImpressionCount() > 0
                ? (double) p.getClickCount() / p.getImpressionCount()
                : 0.0;
        return new PosterAnalyticsResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getPlacement(),
                p.getClickCount(),
                p.getImpressionCount(),
                ctr,
                p.getLastClickAt(),
                p.getLastImpressionAt(),
                p.getCreatedAt()
        );
    }

    // ── A/B testing variant methods ──────────────────────────────────────

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PosterVariantResponse createVariant(UUID posterId, UpsertPosterVariantRequest request) {
        Poster poster = getActiveEntity(posterId);
        PosterVariant variant = new PosterVariant();
        variant.setPoster(poster);
        applyVariantRequest(variant, request);
        PosterVariant saved = posterVariantRepository.save(variant);
        evictPosterCaches(poster, poster.getPlacement(), poster.getSlug());
        return toVariantResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public PosterVariantResponse updateVariant(UUID posterId, UUID variantId, UpsertPosterVariantRequest request) {
        Poster poster = getActiveEntity(posterId);
        PosterVariant variant = posterVariantRepository.findById(variantId)
                .filter(v -> v.getPoster().getId().equals(posterId))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        applyVariantRequest(variant, request);
        PosterVariant saved = posterVariantRepository.save(variant);
        evictPosterCaches(poster, poster.getPlacement(), poster.getSlug());
        return toVariantResponse(saved);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void deleteVariant(UUID posterId, UUID variantId) {
        Poster poster = getActiveEntity(posterId);
        PosterVariant variant = posterVariantRepository.findById(variantId)
                .filter(v -> v.getPoster().getId().equals(posterId))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        posterVariantRepository.delete(variant);
        evictPosterCaches(poster, poster.getPlacement(), poster.getSlug());
    }

    @Override
    public List<PosterVariantResponse> listVariants(UUID posterId) {
        getActiveEntity(posterId);
        return posterVariantRepository.findByPosterIdOrderByCreatedAtAsc(posterId)
                .stream()
                .map(this::toVariantResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = false, timeout = 5)
    public void recordVariantClick(UUID posterId, UUID variantId) {
        boolean variantExists = posterVariantRepository.findById(variantId)
                .filter(v -> v.getPoster().getId().equals(posterId))
                .isPresent();
        if (!variantExists) {
            throw new ResourceNotFoundException("Variant not found: " + variantId);
        }
        posterVariantRepository.incrementClickCount(variantId);
    }

    private void applyVariantRequest(PosterVariant variant, UpsertPosterVariantRequest request) {
        variant.setVariantName(request.variantName().trim());
        variant.setWeight(request.weight() == null ? 50 : request.weight());
        variant.setDesktopImage(trimToNull(request.desktopImage()));
        variant.setMobileImage(trimToNull(request.mobileImage()));
        variant.setTabletImage(trimToNull(request.tabletImage()));
        variant.setSrcsetDesktop(trimToNull(request.srcsetDesktop()));
        variant.setSrcsetMobile(trimToNull(request.srcsetMobile()));
        variant.setSrcsetTablet(trimToNull(request.srcsetTablet()));
        variant.setLinkUrl(trimToNull(request.linkUrl()));
        variant.setActive(request.active() == null || request.active());
    }

    // ── Cache eviction ────────────────────────────────────────────────────

    private void evictPosterCaches(Poster poster, PosterPlacement previousPlacement, String previousSlug) {
        if (poster == null) {
            return;
        }
        evictPostersByPlacementCache(previousPlacement);
        evictPostersByPlacementCache(poster.getPlacement());
        evictPosterByIdCacheKey("any::" + poster.getId());
        if (StringUtils.hasText(previousSlug)) {
            evictPosterByIdCacheKey("any::" + previousSlug);
        }
        if (StringUtils.hasText(poster.getSlug())) {
            evictPosterByIdCacheKey("any::" + poster.getSlug());
        }
    }

    private void evictPostersByPlacementCache(PosterPlacement placement) {
        Cache cache = cacheManager.getCache("postersByPlacement");
        if (cache == null) {
            return;
        }
        cache.evict("all-active");
        if (placement != null) {
            cache.evict("placement::" + placement);
        }
    }

    private void evictPosterByIdCacheKey(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Cache cache = cacheManager.getCache("posterById");
        if (cache != null) {
            cache.evict(key);
        }
    }
}
