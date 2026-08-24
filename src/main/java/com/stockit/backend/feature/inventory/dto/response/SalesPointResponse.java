package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판매처별 재고 및 정보")
public record SalesPointResponse(
        @Schema(description = "판매처 ID", example = "77")
        Long salesPointId,

        @Schema(description = "판매처 코드", example = "GREETING")
        String salesPointCode,

        @Schema(description = "판매처명", example = "그리팅몰")
        String salesPointName,

        @Schema(description = "채널 타입", example = "GREETING")
        String channelType,

        @Schema(description = "현재고", example = "120")
        BigDecimal currentQuantity,

        @Schema(description = "가용재고", example = "110")
        BigDecimal availableQuantity,

        @Schema(description = "예약재고", example = "10")
        BigDecimal reservedQuantity,

        @Schema(description = "위험등급", example = "SAFE")
        String riskGrade,

        @Schema(description = "판매처 행에서는 노출하지 않음(null). 미할당 재고의 보관 위치는 unassignedInventory.locations에서 제공합니다.", example = "")
        String warehouseName,

        @Schema(description = "판매가", example = "12000")
        BigDecimal sellingPrice,

        @Schema(description = "판매처 귀속 상태 (OWNED, ALLOCATED_ONLY, CENTER_ONLY, LOCATION_UNKNOWN)", example = "OWNED")
        String salesPointState,

        @Schema(description = "가격 상태 (AVAILABLE, NOT_LOADED, STALE)", example = "AVAILABLE")
        String priceStatus
) {
    public SalesPointResponse(
            String salesPointCode,
            String salesPointName,
            String channelType,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String riskGrade,
            String warehouseName
    ) {
        this(null, salesPointCode, salesPointName, channelType, currentQuantity, availableQuantity, reservedQuantity,
                riskGrade, warehouseName, null, "OWNED", "NOT_LOADED");
    }

    public SalesPointResponse(
            String salesPointCode,
            String salesPointName,
            String channelType,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String riskGrade,
            String warehouseName,
            BigDecimal sellingPrice
    ) {
        this(null, salesPointCode, salesPointName, channelType, currentQuantity, availableQuantity, reservedQuantity,
                riskGrade, warehouseName, sellingPrice, "OWNED", sellingPrice == null ? "NOT_LOADED" : "AVAILABLE");
    }

    public SalesPointResponse(
            Long salesPointId,
            String salesPointCode,
            String salesPointName,
            String channelType,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String riskGrade,
            String warehouseName
    ) {
        this(salesPointId, salesPointCode, salesPointName, channelType, currentQuantity, availableQuantity, reservedQuantity,
                riskGrade, warehouseName, null, "OWNED", "NOT_LOADED");
    }

    public SalesPointResponse(
            Long salesPointId,
            String salesPointCode,
            String salesPointName,
            String channelType,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String riskGrade,
            String warehouseName,
            BigDecimal sellingPrice
    ) {
        this(salesPointId, salesPointCode, salesPointName, channelType, currentQuantity, availableQuantity, reservedQuantity,
                riskGrade, warehouseName, sellingPrice, "OWNED", sellingPrice == null ? "NOT_LOADED" : "AVAILABLE");
    }
}
