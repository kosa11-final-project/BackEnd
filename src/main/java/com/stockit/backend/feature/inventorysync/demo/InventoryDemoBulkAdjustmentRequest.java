package com.stockit.backend.feature.inventorysync.demo;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InventoryDemoBulkAdjustmentRequest(
        @NotBlank
        @Size(min = 16, max = 80)
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        String clientRequestId,
        @NotNull
        @DecimalMin(value = "0.001")
        @Digits(integer = 12, fraction = 3)
        BigDecimal decreaseQty
) { }
