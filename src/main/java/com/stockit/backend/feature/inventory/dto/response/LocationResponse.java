package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;

public record LocationResponse(
        String warehouseCode,
        String warehouseName,
        BigDecimal quantity
) {
}
