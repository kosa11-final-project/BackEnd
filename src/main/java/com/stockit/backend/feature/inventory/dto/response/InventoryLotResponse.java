package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SKU × 판매처의 LOT 재고")
public record InventoryLotResponse(
        Long id,
        String lotNumber,
        String lotStatus,
        BigDecimal quantity,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        LocalDate manufacturedDate,
        LocalDate receivedDate,
        LocalDate expiryDate,
        LocalDate saleStopDate,
        Integer expiryDays,
        Integer fefoPriority,
        String warehouseCode,
        String warehouseName
) {
}
