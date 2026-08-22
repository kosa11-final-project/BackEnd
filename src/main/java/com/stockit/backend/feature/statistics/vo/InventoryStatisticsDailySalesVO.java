package com.stockit.backend.feature.statistics.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryStatisticsDailySalesVO {
    private LocalDate salesDate;
    private BigDecimal salesQty;
}
