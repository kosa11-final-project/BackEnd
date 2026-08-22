package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.OnlineSalesPointInventoryVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "활성 온라인 판매처별 할당 재고 현황")
public record OnlineSalesPointInventoryResponse(
        @Schema(description = "판매처 ID", example = "1") Long salesPointId,
        @Schema(description = "판매처 코드", example = "GREETING") String salesPointCode,
        @Schema(description = "판매처명", example = "그리팅몰") String salesPointName,
        @Schema(description = "지역 코드", example = "ONLINE") String regionCode,
        @Schema(description = "주소") String address,
        @Schema(description = "재고를 보관하는 활성 물류센터 수", example = "1") long storageWarehouseCount,
        @Schema(description = "현재고") BigDecimal currentStock,
        @Schema(description = "판매 가능 재고") BigDecimal availableStock,
        @Schema(description = "소비기한 D-30 이내 수량") BigDecimal nearExpiryStock,
        @Schema(description = "향후 30일 예상 폐기수량") BigDecimal expectedDisposalQty,
        @Schema(description = "위험(CRITICAL) 고유 SKU 수") long riskSkuCount
) {

    public static OnlineSalesPointInventoryResponse from(OnlineSalesPointInventoryVO value) {
        return new OnlineSalesPointInventoryResponse(
                value.getSalesPointId(),
                value.getSalesPointCode(),
                value.getSalesPointName(),
                value.getRegionCode(),
                value.getAddress(),
                value.getStorageWarehouseCount(),
                value.getCurrentStock(),
                value.getAvailableStock(),
                value.getNearExpiryStock(),
                value.getExpectedDisposalQty(),
                value.getRiskSkuCount()
        );
    }
}
