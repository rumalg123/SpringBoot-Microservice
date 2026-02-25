package com.rumal.search_service.dto;

public record AutocompleteSuggestion(
        String text,
        String type,
        String id,
        String slug,
        String mainImage
) {}
