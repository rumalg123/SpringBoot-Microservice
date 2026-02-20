package com.rumal.product_service.service;

import java.text.Normalizer;
import java.util.Locale;

final class SlugUtils {

    private SlugUtils() {
    }

    static String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        return slug;
    }
}
