package com.stockit.backend.feature.statistics.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyStatisticsActionVO {

    private Long strategyOptionId;
    private String actionType;
}
