package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InventoryItemResponse(
        String rowId,
        String productCode,
        String productName,
        String supplierName,
        String skuCode,
        String skuName,
        String imageUrl,
        Long categoryId,
        String categoryName,
        InventoryCategoryResponse category,
        String channelType,
        String salesPointCode,
        String salesPointName,
        String storageType,
        BigDecimal sellingPrice,
        BigDecimal currentQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        BigDecimal safetyQuantity,
        String inventoryFactState,
        RiskResponse risk,
        List<LocationResponse> locations,
        int locationCount,
        List<SalesPointResponse> salesPoints,
        Integer ownerSalesPointCount,
        UnassignedInventoryResponse unassignedInventory,
        Integer lotCount,
        Integer nearestExpiryDays,
        LocalDate nearestExpiryDate,
        Instant updatedAt
) {

    public InventoryItemResponse {
        locations = List.copyOf(locations == null ? List.of() : locations);
        salesPoints = List.copyOf(salesPoints == null ? List.of() : salesPoints);
        unassignedInventory = unassignedInventory == null ? UnassignedInventoryResponse.empty() : unassignedInventory;
    }

    public InventoryItemResponse(
            String rowId,
            String productCode,
            String productName,
            String skuCode,
            String skuName,
            String imageUrl,
            String channelType,
            String salesPointCode,
            String salesPointName,
            String storageType,
            BigDecimal sellingPrice,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            BigDecimal safetyQuantity,
            String inventoryFactState,
            RiskResponse risk,
            List<LocationResponse> locations,
            int locationCount,
            List<SalesPointResponse> salesPoints,
            Integer lotCount,
            Integer nearestExpiryDays,
            LocalDate nearestExpiryDate,
            Instant updatedAt
    ) {
        this(rowId, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null, channelType, salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, risk, locations, locationCount, salesPoints, salesPoints == null ? 0 : salesPoints.size(), UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt);
    }
}
