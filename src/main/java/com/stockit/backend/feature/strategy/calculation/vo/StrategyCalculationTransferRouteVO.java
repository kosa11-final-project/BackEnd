package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationTransferRouteVO {
    private Long transferRouteId;
    private Long sourceWarehouseId;
    private Long sourceSalesPointId;
    private Long destinationWarehouseId;
    private Long destinationSalesPointId;
    private BigDecimal distanceKm;
    private String distanceSource;
    private String distanceRouteOption;
    private LocalDateTime distanceCalculatedAt;
}
