package com.rumal.product_service.service;

import com.rumal.product_service.exception.ValidationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductContentSanitizer {

    private static final Safelist DESCRIPTION_SAFE_LIST = Safelist.basic()
            .addProtocols("a", "href", "http", "https", "mailto");

    public String sanitizePlainText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String sanitized = Jsoup.parse(value).text().trim();
        if (!StringUtils.hasText(sanitized)) {
            throw new ValidationException(fieldName + " cannot be blank after sanitization");
        }
        return sanitized;
    }

    public String sanitizeOptionalPlainText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = Jsoup.parse(value).text().trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    public String sanitizeRichText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String sanitized = Jsoup.clean(value, "", DESCRIPTION_SAFE_LIST, outputSettings).trim();
        if (!StringUtils.hasText(Jsoup.parse(sanitized).text())) {
            throw new ValidationException(fieldName + " cannot be blank after sanitization");
        }
        return sanitized;
    }
}
