package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.RiskSalesPointVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "위험재고 보유 판매처 순위")
public record RiskSalesPointResponse(
        @Schema(description = "순위", example = "1") int rank,
        @Schema(description = "판매처 ID") Long salesPointId,
        @Schema(description = "판매처 코드", example = "GREETING") String salesPointCode,
        @Schema(description = "판매처명", example = "그리팅몰") String salesPointName,
        @Schema(description = "판매 채널 유형", example = "ONLINE") String channelType,
        @Schema(description = "지역 코드", example = "ONLINE") String regionCode,
        @Schema(description = "판매 가능 재고") BigDecimal availableStock,
        @Schema(description = "위험(CRITICAL) 고유 SKU 수") long riskSkuCount,
        @Schema(description = "향후 30일 예상 폐기수량") BigDecimal expectedDisposalQty,
        @Schema(description = "소비기한 D-30 이내 수량") BigDecimal nearExpiryStock
) {

    public static RiskSalesPointResponse from(int rank, RiskSalesPointVO value) {
        return new RiskSalesPointResponse(
                rank,
                value.getSalesPointId(),
                value.getSalesPointCode(),
                value.getSalesPointName(),
                value.getChannelType(),
                value.getRegionCode(),
                value.getAvailableStock(),
                value.getRiskSkuCount(),
                value.getExpectedDisposalQty(),
                value.getNearExpiryStock()
        );
    }
}
