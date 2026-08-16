package com.stockit.backend.feature.inventory.dto.response;

import java.util.List;

/** SKU에 연결된 최하위 카테고리와 대-중-소 전체 경로입니다. */
public record InventoryCategoryResponse(
        CategoryPathItemResponse leaf,
        List<CategoryPathItemResponse> path
) {

    public InventoryCategoryResponse {
        path = List.copyOf(path == null ? List.of() : path);
    }
}
