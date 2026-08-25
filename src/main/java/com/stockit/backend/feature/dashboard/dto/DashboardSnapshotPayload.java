package com.stockit.backend.feature.dashboard.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OfflineStoreInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OnlineSalesPointInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.RiskSalesPointResponse;
import com.stockit.backend.feature.dashboard.dto.response.UrgentSkuResponse;
import com.stockit.backend.feature.dashboard.dto.response.WarehouseInventoryResponse;

public record DashboardSnapshotPayload(
        DashboardSummaryResponse summary,
        List<WarehouseInventoryResponse> warehouses,
        List<OnlineSalesPointInventoryResponse> onlineSalesPoints,
        List<OfflineStoreInventoryResponse> offlineStores,
        List<RiskSalesPointResponse> riskSalesPointsTop10,
        List<UrgentSkuResponse> urgentSkusTop5,
        Map<Long, List<UrgentSkuResponse>> urgentSkusBySalesPoint
) {

    public DashboardSnapshotPayload {
        urgentSkusBySalesPoint = normalizeUrgentSkusBySalesPoint(urgentSkusBySalesPoint, urgentSkusTop5);
    }

    /**
     * 판매처별 목록이 추가되기 전에 저장된 스냅샷은 전역 TOP 5만 가지고 있다.
     * 해당 스냅샷도 선택 판매처의 긴급 SKU를 표시할 수 있도록 배정 판매처 기준으로 보완한다.
     */
    private static Map<Long, List<UrgentSkuResponse>> normalizeUrgentSkusBySalesPoint(
            Map<Long, List<UrgentSkuResponse>> value,
            List<UrgentSkuResponse> fallback
    ) {
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (fallback == null || fallback.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<UrgentSkuResponse>> grouped = new LinkedHashMap<>();
        for (UrgentSkuResponse sku : fallback) {
            if (sku == null || sku.allocatedSalesPointId() == null) {
                continue;
            }
            grouped.computeIfAbsent(sku.allocatedSalesPointId(), ignored -> new ArrayList<>()).add(sku);
        }
        grouped.replaceAll((salesPointId, skus) -> List.copyOf(skus));
        return grouped.isEmpty() ? Map.of() : Map.copyOf(grouped);
    }

    public static DashboardSnapshotPayload from(DashboardResponse response) {
        return new DashboardSnapshotPayload(
                response.summary(),
                response.warehouses(),
                response.onlineSalesPoints(),
                response.offlineStores(),
                response.riskSalesPointsTop10(),
                response.urgentSkusTop5(),
                response.urgentSkusBySalesPoint()
        );
    }

    public DashboardResponse toResponse(Instant calculatedAt) {
        return new DashboardResponse(
                summary,
                warehouses,
                onlineSalesPoints,
                offlineStores,
                riskSalesPointsTop10,
                urgentSkusTop5,
                urgentSkusBySalesPoint,
                calculatedAt
        );
    }
}
