package com.rumal.poster_service.dto;

import java.util.List;

public record PosterImagePrepareUploadResponse(List<PosterImagePresignedUpload> uploads) {
}
