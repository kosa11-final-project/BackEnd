package com.stockit.backend.feature.statistics.dto;

import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;

public record StatisticsSnapshotPayload(
        InventoryStatisticsSummaryResponse inventory
) {
}
