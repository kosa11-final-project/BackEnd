package com.stockit.backend.feature.inventory.service;

import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryFilterOptionsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventorySummaryResponse;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;

public interface InventoryQueryService {

    InventoryListResponse find(InventoryQuery query);

    InventorySummaryResponse summary(InventoryQuery query);

    InventoryFilterOptionsResponse filterOptions();

    InventoryDetailResponse detail(String skuCode, String salesPointCode);

    InventoryLotsResponse lots(String skuCode, String salesPointCode);
}
