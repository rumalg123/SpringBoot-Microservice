package com.rumal.poster_service.dto;

import java.time.Instant;

public record PosterImagePresignedUpload(
        String key,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
