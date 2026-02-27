package com.rumal.poster_service.service;

import com.rumal.poster_service.dto.PosterAnalyticsResponse;
import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.PosterVariantResponse;
import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.dto.UpsertPosterVariantRequest;
import com.rumal.poster_service.entity.PosterPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PosterService {
    PosterResponse create(UpsertPosterRequest request);
    PosterResponse update(UUID id, UpsertPosterRequest request);
    PosterResponse getByIdOrSlug(String idOrSlug);
    PosterResponse getCachedPosterByIdOrSlug(String idOrSlug);
    List<PosterResponse> listActiveByPlacement(PosterPlacement placement);
    Page<PosterResponse> listActiveByPlacement(PosterPlacement placement, Pageable pageable);
    List<PosterResponse> listAllActive();
    Page<PosterResponse> listAllActive(Pageable pageable);
    List<PosterResponse> listAllNonDeleted();
    Page<PosterResponse> listAllNonDeleted(Pageable pageable);
    Page<PosterResponse> listDeleted();
    Page<PosterResponse> listDeleted(Pageable pageable);
    void softDelete(UUID id);
    PosterResponse restore(UUID id);
    boolean isSlugAvailable(String slug, UUID excludeId);
    void recordClick(UUID id);
    void recordImpression(UUID id);
    PosterAnalyticsResponse getAnalytics(UUID id);
    List<PosterAnalyticsResponse> listAnalytics();

    // A/B testing variant methods
    PosterVariantResponse createVariant(UUID posterId, UpsertPosterVariantRequest request);
    PosterVariantResponse updateVariant(UUID posterId, UUID variantId, UpsertPosterVariantRequest request);
    void deleteVariant(UUID posterId, UUID variantId);
    List<PosterVariantResponse> listVariants(UUID posterId);
    void recordVariantClick(UUID posterId, UUID variantId);
    void recordVariantImpression(UUID posterId, UUID variantId);
}
