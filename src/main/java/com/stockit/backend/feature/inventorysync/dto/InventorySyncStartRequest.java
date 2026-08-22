package com.stockit.backend.feature.inventorysync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InventorySyncStartRequest(
        @NotBlank @Size(min = 16, max = 80)
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        String clientRequestId
) {
}
