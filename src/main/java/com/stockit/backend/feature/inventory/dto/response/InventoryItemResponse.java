package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InventoryItemResponse(
        String rowId,
        Long skuId,
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
        String shortageYn,
        RiskResponse risk,
        List<LocationResponse> locations,
        int locationCount,
        List<SalesPointResponse> salesPoints,
        Integer ownerSalesPointCount,
        UnassignedInventoryResponse unassignedInventory,
        Integer lotCount,
        Integer nearestExpiryDays,
        LocalDate nearestExpiryDate,
        Instant updatedAt,
        BigDecimal expectedDisposalQuantity
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
        this(rowId, null, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null, channelType, salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, null, risk, locations, locationCount, salesPoints, salesPoints == null ? 0 : salesPoints.size(), UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt, null);
    }

    public InventoryItemResponse(
            String rowId,
            Long skuId,
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
        this(rowId, skuId, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null, channelType, salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, null, risk, locations, locationCount, salesPoints, salesPoints == null ? 0 : salesPoints.size(), UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt, null);

    }

    public InventoryItemResponse(
            String rowId,
            Long skuId,
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
            Instant updatedAt,
            BigDecimal expectedDisposalQuantity
    ) {
        this(rowId, skuId, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null,
                channelType, salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity,
                availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, null, risk, locations,
                locationCount, salesPoints, salesPoints == null ? 0 : salesPoints.size(),
                UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt,
                expectedDisposalQuantity);
    }

    /** 예상 폐기수량 필드 추가 전 전체 생성자 호출과의 호환을 유지합니다. */
    public InventoryItemResponse(
            String rowId,
            Long skuId,
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
            String shortageYn,
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
        this(rowId, skuId, productCode, productName, supplierName, skuCode, skuName, imageUrl, categoryId,
                categoryName, category, channelType, salesPointCode, salesPointName, storageType, sellingPrice,
                currentQuantity, availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, shortageYn,
                risk, locations, locationCount, salesPoints, ownerSalesPointCount, unassignedInventory, lotCount,
                nearestExpiryDays, nearestExpiryDate, updatedAt, null);
    }

}
