package com.stockit.backend.feature.dashboard.dto;

import java.time.Instant;
import java.util.List;

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
        List<UrgentSkuResponse> urgentSkusTop5
) {

    public static DashboardSnapshotPayload from(DashboardResponse response) {
        return new DashboardSnapshotPayload(
                response.summary(),
                response.warehouses(),
                response.onlineSalesPoints(),
                response.offlineStores(),
                response.riskSalesPointsTop10(),
                response.urgentSkusTop5()
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
                calculatedAt
        );
    }
}
