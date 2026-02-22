package com.rumal.poster_service.service;

import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.entity.PosterPlacement;

import java.util.List;
import java.util.UUID;

public interface PosterService {
    PosterResponse create(UpsertPosterRequest request);
    PosterResponse update(UUID id, UpsertPosterRequest request);
    PosterResponse getByIdOrSlug(String idOrSlug);
    List<PosterResponse> listActiveByPlacement(PosterPlacement placement);
    List<PosterResponse> listAllActive();
    List<PosterResponse> listAllNonDeleted();
    List<PosterResponse> listDeleted();
    void softDelete(UUID id);
    PosterResponse restore(UUID id);
    boolean isSlugAvailable(String slug, UUID excludeId);
}
