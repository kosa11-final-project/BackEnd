package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.WarehouseInventoryVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "물류센터별 재고 현황")
public record WarehouseInventoryResponse(
        @Schema(description = "물류센터 ID", example = "1") Long warehouseId,
        @Schema(description = "물류센터 코드", example = "SEONGNAM") String warehouseCode,
        @Schema(description = "물류센터명", example = "성남 스마트푸드센터") String warehouseName,
        @Schema(description = "지역 코드", example = "GYEONGGI") String regionCode,
        @Schema(description = "주소") String address,
        @Schema(description = "현재고") BigDecimal currentStock,
        @Schema(description = "판매 가능 재고") BigDecimal availableStock,
        @Schema(description = "소비기한 D-30 이내 수량") BigDecimal nearExpiryStock,
        @Schema(description = "출고 예정 수량") BigDecimal outboundStock,
        @Schema(description = "위험(CRITICAL) 고유 SKU 수") long riskSkuCount
) {

    public static WarehouseInventoryResponse from(WarehouseInventoryVO value) {
        return new WarehouseInventoryResponse(
                value.getWarehouseId(),
                value.getWarehouseCode(),
                value.getWarehouseName(),
                value.getRegionCode(),
                value.getAddress(),
                value.getCurrentStock(),
                value.getAvailableStock(),
                value.getNearExpiryStock(),
                value.getOutboundStock(),
                value.getRiskSkuCount()
        );
    }
}
