package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationPriceVO {
    private Long skuChannelPriceId;
    private Long salesPointId;
    private BigDecimal sellingPrice;
    private BigDecimal minimumSellingPrice;
    private BigDecimal actualPrice;
    private BigDecimal paymentFee;
    private BigDecimal logisticsCost;
}
