package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 목록 DTO와 같은 flat field를 유지하면서 선택 행의 LOT 상세 및 판매처별 판매가 목록을 함께 제공하는 응답입니다.
 * FrontEnd mapper가 목록/상세 응답을 같은 방식으로 정규화할 수 있도록 중첩 item을 두지 않습니다.
 */
@Schema(description = "선택한 SKU × 판매처 상세")
public record InventoryDetailResponse(
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
        RiskResponse risk,
        List<LocationResponse> locations,
        int locationCount,
        List<SalesPointResponse> salesPoints,
        UnassignedInventoryResponse unassignedInventory,
        Integer lotCount,
        Integer nearestExpiryDays,
        LocalDate nearestExpiryDate,
        Instant updatedAt,
        List<InventoryLotResponse> lots,
        List<SkuChannelPriceResponse> channelPrices,
        BigDecimal expectedDisposalQuantity
) {

    public InventoryDetailResponse {
        locations = List.copyOf(locations == null ? List.of() : locations);
        salesPoints = List.copyOf(salesPoints == null ? List.of() : salesPoints);
        unassignedInventory = unassignedInventory == null ? UnassignedInventoryResponse.empty() : unassignedInventory;
        lots = List.copyOf(lots == null ? List.of() : lots);
        channelPrices = List.copyOf(channelPrices == null ? List.of() : channelPrices);
    }

    /** channelPrices가 없는 기존 상세 응답 생성부와의 하위 호환용 생성자입니다. */
    public InventoryDetailResponse(
            String rowId,
            String productCode,
            String productName,
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
            Integer lotCount,
            Integer nearestExpiryDays,
            LocalDate nearestExpiryDate,
            Instant updatedAt,
            List<InventoryLotResponse> lots
    ) {
        this(rowId, null, productCode, productName, null, skuCode, skuName, imageUrl, categoryId, categoryName, category,
                channelType, salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity,
                availableQuantity, reservedQuantity, safetyQuantity, inventoryFactState, risk, locations,
                locationCount, salesPoints, UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate,
                updatedAt, lots, List.of(), null);
    }

    /** 레거시 테스트 및 코드 호환용 생성자입니다. */
    public InventoryDetailResponse(
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
            Instant updatedAt,
            List<InventoryLotResponse> lots
    ) {
        this(rowId, null, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null, channelType,
                salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity,
                reservedQuantity, safetyQuantity, inventoryFactState, risk, locations, locationCount, salesPoints,
                UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt, lots, List.of(), null);
    }

    public InventoryDetailResponse(
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
            List<InventoryLotResponse> lots
    ) {
        this(rowId, skuId, productCode, productName, null, skuCode, skuName, imageUrl, null, null, null, channelType,
                salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity,
                reservedQuantity, safetyQuantity, inventoryFactState, risk, locations, locationCount, salesPoints,
                UnassignedInventoryResponse.empty(), lotCount, nearestExpiryDays, nearestExpiryDate, updatedAt, lots, List.of(), null);
    }
}
