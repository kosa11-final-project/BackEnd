package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 목록 DTO와 같은 flat field를 유지하면서 선택 행의 LOT 상세를 함께 제공하는 응답입니다.
 * FrontEnd mapper가 목록/상세 응답을 같은 방식으로 정규화할 수 있도록 중첩 item을 두지 않습니다.
 */
@Schema(description = "선택한 SKU × 판매처 상세")
public record InventoryDetailResponse(
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
        BigDecimal dailySales,
        BigDecimal forecast14Days,
        Instant updatedAt,
        List<InventoryLotResponse> lots
) {

    public InventoryDetailResponse {
        locations = List.copyOf(locations == null ? List.of() : locations);
        salesPoints = List.copyOf(salesPoints == null ? List.of() : salesPoints);
        lots = List.copyOf(lots == null ? List.of() : lots);
    }

    /** 기존 상세 응답 생성부와의 하위 호환용 생성자입니다. */
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
            BigDecimal dailySales,
            BigDecimal forecast14Days,
            Instant updatedAt,
            List<InventoryLotResponse> lots
    ) {
        this(rowId, productCode, productName, skuCode, skuName, imageUrl, null, null, null, channelType,
                salesPointCode, salesPointName, storageType, sellingPrice, currentQuantity, availableQuantity,
                reservedQuantity, safetyQuantity, inventoryFactState, risk, locations, locationCount, salesPoints,
                lotCount, nearestExpiryDays, nearestExpiryDate, dailySales, forecast14Days, updatedAt, lots);
    }
}
