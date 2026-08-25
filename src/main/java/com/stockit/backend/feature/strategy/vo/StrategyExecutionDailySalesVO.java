package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionDailySalesVO {
    private LocalDate salesDate;
    private Long salesPointId;
    private String salesPointCode;
    private String salesPointName;
    private BigDecimal quantity;
    private BigDecimal revenue;
}
