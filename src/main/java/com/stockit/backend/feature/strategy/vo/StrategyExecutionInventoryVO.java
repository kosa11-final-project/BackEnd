package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionInventoryVO {
    private String locationType;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal beforeQuantity;
    private BigDecimal currentQuantity;
    private BigDecimal safetyStockQuantity;
}
