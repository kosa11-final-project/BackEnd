package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;

public record SalesPointResponse(
        String salesPointCode,
        String salesPointName,
        String channelType,
        BigDecimal currentQuantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        String riskGrade,
        String warehouseName,
        BigDecimal sellingPrice
) {
    public SalesPointResponse(
            String salesPointCode,
            String salesPointName,
            String channelType,
            BigDecimal currentQuantity,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String riskGrade,
            String warehouseName
    ) {
        this(salesPointCode, salesPointName, channelType, currentQuantity, availableQuantity, reservedQuantity, riskGrade, warehouseName, null);
    }
}
