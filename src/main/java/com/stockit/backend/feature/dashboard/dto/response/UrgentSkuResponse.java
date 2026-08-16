package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.UrgentSkuVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "긴급 처리 대상 SKU와 실제 재고 위치")
public record UrgentSkuResponse(
        @Schema(description = "순위", example = "1") int rank,
        @Schema(description = "SKU ID") Long skuId,
        @Schema(description = "SKU 코드", example = "GF-SAL-GRN-05") String skuCode,
        @Schema(description = "SKU명", example = "그린믹스 · 5팩") String skuName,
        @Schema(description = "실제 재고 위치 유형", example = "WAREHOUSE") String stockLocationType,
        @Schema(description = "실제 재고 위치 ID") Long stockLocationId,
        @Schema(description = "실제 재고 위치 코드", example = "SEONGNAM") String stockLocationCode,
        @Schema(description = "실제 재고 위치명", example = "성남 스마트푸드센터") String stockLocationName,
        @Schema(description = "관련 또는 배정 판매처 ID", nullable = true) Long allocatedSalesPointId,
        @Schema(description = "관련 또는 배정 판매처 코드", nullable = true, example = "GREETING") String allocatedSalesPointCode,
        @Schema(description = "관련 또는 배정 판매처명", nullable = true) String allocatedSalesPointName,
        @Schema(description = "소비기한까지 남은 일수", example = "12") Integer expiryDaysLeft,
        @Schema(description = "판매중지일까지 남은 일수", example = "5") Integer saleStopDaysLeft,
        @Schema(description = "향후 30일 예상 폐기수량", example = "86") BigDecimal expectedDisposalQty,
        @Schema(description = "대표 위험 사유", example = "소비기한 내 판매 소진이 어렵습니다.") String reasonMessage
) {

    public static UrgentSkuResponse from(int rank, UrgentSkuVO value) {
        return new UrgentSkuResponse(
                rank,
                value.getSkuId(),
                value.getSkuCode(),
                value.getSkuName(),
                value.getStockLocationType(),
                value.getStockLocationId(),
                value.getStockLocationCode(),
                value.getStockLocationName(),
                value.getAllocatedSalesPointId(),
                value.getAllocatedSalesPointCode(),
                value.getAllocatedSalesPointName(),
                value.getExpiryDaysLeft(),
                value.getSaleStopDaysLeft(),
                value.getExpectedDisposalQty(),
                value.getReasonMessage()
        );
    }
}
