package com.rumal.poster_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PosterImageNameRequest(
        @NotEmpty
        @Size(max = 10)
        List<String> fileNames
) {
}
