package com.stockit.backend.feature.inventory.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "통합 재고 목록 필터 기준정보")
public record InventoryFilterOptionsResponse(
        List<InventoryOptionResponse> channels,
        List<InventoryOptionResponse> salesPoints,
        List<InventoryOptionResponse> warehouses,
        List<InventoryOptionResponse> regions,
        List<InventoryOptionResponse> categories,
        List<InventoryOptionResponse> storageTypes,
        List<InventoryOptionResponse> riskGrades,
        List<InventoryOptionResponse> assessmentStatuses
) {

    public InventoryFilterOptionsResponse {
        channels = List.copyOf(channels == null ? List.of() : channels);
        salesPoints = List.copyOf(salesPoints == null ? List.of() : salesPoints);
        warehouses = List.copyOf(warehouses == null ? List.of() : warehouses);
        regions = List.copyOf(regions == null ? List.of() : regions);
        categories = List.copyOf(categories == null ? List.of() : categories);
        storageTypes = List.copyOf(storageTypes == null ? List.of() : storageTypes);
        riskGrades = List.copyOf(riskGrades == null ? List.of() : riskGrades);
        assessmentStatuses = List.copyOf(assessmentStatuses == null ? List.of() : assessmentStatuses);
    }
}
