package com.stockit.backend.feature.inventory.dto.response;

import java.util.List;

public record InventoryListResponse(
        List<InventoryItemResponse> items,
        long totalCount,
        int page,
        int size,
        int totalPages,
        boolean isFilterEmpty
) {

    public InventoryListResponse {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
