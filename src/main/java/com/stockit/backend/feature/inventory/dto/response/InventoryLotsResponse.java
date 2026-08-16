package com.stockit.backend.feature.inventory.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LOT 조회 결과")
public record InventoryLotsResponse(
        List<InventoryLotResponse> items,
        long totalCount
) {

    public InventoryLotsResponse {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
