package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.OfflineStoreInventoryVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "활성 오프라인 매장별 재고 현황")
public record OfflineStoreInventoryResponse(
        @Schema(description = "판매처 ID", example = "3") Long salesPointId,
        @Schema(description = "판매처 코드", example = "DEPT_THEHYUNDAI_SEOUL") String salesPointCode,
        @Schema(description = "판매처명", example = "더현대 서울") String salesPointName,
        @Schema(description = "지역 코드", example = "SEOUL") String regionCode,
        @Schema(description = "주소") String address,
        @Schema(description = "현재고") BigDecimal currentStock,
        @Schema(description = "판매 가능 재고") BigDecimal availableStock,
        @Schema(description = "소비기한 D-30 이내 수량") BigDecimal nearExpiryStock,
        @Schema(description = "향후 30일 예상 폐기수량") BigDecimal expectedDisposalQty,
        @Schema(description = "위험(CRITICAL) 고유 SKU 수") long riskSkuCount
) {

    public static OfflineStoreInventoryResponse from(OfflineStoreInventoryVO value) {
        return new OfflineStoreInventoryResponse(
                value.getSalesPointId(),
                value.getSalesPointCode(),
                value.getSalesPointName(),
                value.getRegionCode(),
                value.getAddress(),
                value.getCurrentStock(),
                value.getAvailableStock(),
                value.getNearExpiryStock(),
                value.getExpectedDisposalQty(),
                value.getRiskSkuCount()
        );
    }
}
