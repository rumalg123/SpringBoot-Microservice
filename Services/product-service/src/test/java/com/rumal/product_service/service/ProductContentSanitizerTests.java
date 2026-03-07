package com.rumal.product_service.service;

import com.rumal.product_service.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductContentSanitizerTests {

    private final ProductContentSanitizer sanitizer = new ProductContentSanitizer();

    @Test
    void sanitizeRichTextRemovesActiveContent() {
        String sanitized = sanitizer.sanitizeRichText(
                "<p>Safe</p><script>alert(1)</script><a href=\"javascript:alert(1)\" onclick=\"hack()\">Click</a>",
                "description"
        );

        assertTrue(sanitized.contains("<p>Safe</p>"));
        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("javascript:"));
        assertFalse(sanitized.contains("onclick"));
    }

    @Test
    void sanitizePlainTextRejectsBlankAfterSanitization() {
        assertThrows(ValidationException.class, () -> sanitizer.sanitizePlainText("<img src=x onerror=alert(1)>", "name"));
    }
}
