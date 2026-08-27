package com.stockit.backend.feature.statistics.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyStatisticsScopeVO {

    private Long finalSelectionId;
    private String scopeType;
    private String scopeCode;
    private String scopeName;
}
