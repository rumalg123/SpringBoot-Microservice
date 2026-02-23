package com.rumal.access_service.dto;

import java.util.List;

public record AccessChangeAuditPageResponse(
        List<AccessChangeAuditResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
