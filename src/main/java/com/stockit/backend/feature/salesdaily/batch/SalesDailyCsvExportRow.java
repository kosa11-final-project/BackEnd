package com.stockit.backend.feature.salesdaily.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDailyCsvExportRow(
        LocalDate salesDate,
        Long skuId,
        Long salesPointId,
        BigDecimal netSalesQty
) {
}
