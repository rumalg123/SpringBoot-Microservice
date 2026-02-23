package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromotionApprovalDecisionRequest(
        @NotBlank @Size(max = 1000) String note
) {
}
