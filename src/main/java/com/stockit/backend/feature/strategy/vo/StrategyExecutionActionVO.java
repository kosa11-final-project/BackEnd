package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionActionVO {
    private Long strategyActionId;
    private Long strategyOptionId;
    private String actionType;
    private BigDecimal actionQuantity;
    private Integer actionOrder;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long sourceSalesPointId;
    private String sourceSalesPointCode;
    private String sourceSalesPointName;
    private Long targetSalesPointId;
    private String targetSalesPointCode;
    private String targetSalesPointName;
    private Long sourceWarehouseId;
    private String sourceWarehouseCode;
    private String sourceWarehouseName;
    private Long destinationWarehouseId;
    private String destinationWarehouseCode;
    private String destinationWarehouseName;
}
