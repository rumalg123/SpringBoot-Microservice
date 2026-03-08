package com.rumal.poster_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PosterImagePrepareUploadRequest(
        @NotEmpty
        @Size(max = 10)
        List<@Valid PosterImagePrepareUploadItem> files
) {
}
