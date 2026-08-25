package com.stockit.backend.feature.inventorysync.demo;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.stockit.backend.feature.inventorysync.InventorySyncSourceOrder;

public record InventoryDemoAdjustmentRequest(
        @NotBlank @Size(min = 16, max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientRequestId,
        @NotEmpty @Size(max = 100) List<@Valid Item> items
) {
    public record Item(
            @NotBlank @Pattern(regexp = InventorySyncSourceOrder.VALIDATION_PATTERN) String sourceType,
            @NotBlank @Size(max = 300) @Pattern(regexp = "[^\\p{Cntrl}\\r\\n]+") String sourceRecordKey,
            @NotNull @DecimalMin(value = "0.001") BigDecimal decreaseQty
    ) { }
}
