package com.rumal.poster_service.controller;

import com.rumal.poster_service.dto.PosterAnalyticsResponse;
import com.rumal.poster_service.dto.PosterImageNameRequest;
import com.rumal.poster_service.dto.PosterImageUploadResponse;
import com.rumal.poster_service.dto.PosterResponse;
import com.rumal.poster_service.dto.PosterVariantResponse;
import com.rumal.poster_service.dto.UpsertPosterRequest;
import com.rumal.poster_service.dto.UpsertPosterVariantRequest;
import com.rumal.poster_service.security.InternalRequestVerifier;
import com.rumal.poster_service.service.PosterService;
import com.rumal.poster_service.storage.PosterImageStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/posters")
@RequiredArgsConstructor
public class AdminPosterController {

    private final PosterService posterService;
    private final PosterImageStorageService posterImageStorageService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<PosterResponse> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.listAllNonDeleted(pageable);
    }

    @GetMapping("/deleted")
    public Page<PosterResponse> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.listDeleted(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PosterResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody UpsertPosterRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.create(request);
    }

    @PutMapping("/{id}")
    public PosterResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPosterRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        posterService.softDelete(id);
    }

    @PostMapping("/{id}/restore")
    public PosterResponse restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.restore(id);
    }

    @GetMapping("/analytics")
    public List<PosterAnalyticsResponse> listAnalytics(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.listAnalytics();
    }

    @GetMapping("/{id}/analytics")
    public PosterAnalyticsResponse getAnalytics(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.getAnalytics(id);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PosterImageUploadResponse uploadImages(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "keys", required = false) List<String> keys
    ) {
        internalRequestVerifier.verify(internalAuth);
        return new PosterImageUploadResponse(posterImageStorageService.uploadImages(files, keys));
    }

    @PostMapping("/images/names")
    @ResponseStatus(HttpStatus.CREATED)
    public PosterImageUploadResponse generateImageNames(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody PosterImageNameRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return new PosterImageUploadResponse(posterImageStorageService.generateImageNames(request.fileNames()));
    }

    // ── A/B testing variant endpoints ─────────────────────────────────────

    @GetMapping("/{posterId}/variants")
    public List<PosterVariantResponse> listVariants(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID posterId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.listVariants(posterId);
    }

    @PostMapping("/{posterId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public PosterVariantResponse createVariant(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID posterId,
            @Valid @RequestBody UpsertPosterVariantRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.createVariant(posterId, request);
    }

    @PutMapping("/{posterId}/variants/{variantId}")
    public PosterVariantResponse updateVariant(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID posterId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpsertPosterVariantRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return posterService.updateVariant(posterId, variantId, request);
    }

    @DeleteMapping("/{posterId}/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID posterId,
            @PathVariable UUID variantId
    ) {
        internalRequestVerifier.verify(internalAuth);
        posterService.deleteVariant(posterId, variantId);
    }
}
