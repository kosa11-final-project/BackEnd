package com.stockit.backend.feature.dashboard.dto.response;

import java.math.BigDecimal;

import com.stockit.backend.feature.dashboard.vo.DashboardSummaryVO;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전국 온·오프라인 핵심 재고 지표")
public record DashboardSummaryResponse(
        @Schema(description = "전국 총현재고", example = "4800")
        BigDecimal totalCurrentStock,
        @Schema(description = "전국 판매 가능 재고", example = "4062")
        BigDecimal totalAvailableStock,
        @Schema(description = "전국 고유 위험(CRITICAL) SKU 수", example = "5")
        long criticalSkuCount,
        @Schema(description = "전국 고유 주의(WARNING) SKU 수", example = "7")
        long warningSkuCount,
        @Schema(description = "전국 고유 위험·주의 SKU 합계", example = "12")
        long riskAndWarningSkuCount,
        @Schema(description = "안전재고 미만인 전국 고유 SKU 수", example = "9")
        long shortageSkuCount,
        @Schema(description = "향후 30일 예상 폐기수량", example = "519")
        BigDecimal expectedDisposalQty
) {

    public static DashboardSummaryResponse from(DashboardSummaryVO value) {
        long criticalCount = value.getCriticalSkuCount();
        long warningCount = value.getWarningSkuCount();
        return new DashboardSummaryResponse(
                value.getTotalCurrentStock(),
                value.getTotalAvailableStock(),
                criticalCount,
                warningCount,
                criticalCount + warningCount,
                value.getShortageSkuCount(),
                value.getExpectedDisposalQty()
        );
    }
}
