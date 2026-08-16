package com.stockit.backend.feature.dashboard.dto.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "재고 운영 대시보드 전체 응답")
public record DashboardResponse(
        @Schema(description = "전국 핵심 재고 지표")
        DashboardSummaryResponse summary,
        @Schema(description = "활성 물류센터 재고 현황")
        List<WarehouseInventoryResponse> warehouses,
        @Schema(description = "활성 오프라인 매장 재고 현황")
        List<OfflineStoreInventoryResponse> offlineStores,
        @Schema(description = "위험재고 보유 온·오프라인 판매처 TOP 10")
        List<RiskSalesPointResponse> riskSalesPointsTop10,
        @Schema(description = "긴급 처리 대상 SKU와 실제 재고 위치 TOP 5")
        List<UrgentSkuResponse> urgentSkusTop5,
        @Schema(description = "대시보드 스냅샷 집계 기준 시각", example = "2026-08-15T01:05:00Z")
        Instant calculatedAt
) {
}
