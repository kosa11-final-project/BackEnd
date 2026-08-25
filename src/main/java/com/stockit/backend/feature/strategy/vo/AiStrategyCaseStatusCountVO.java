package com.stockit.backend.feature.strategy.vo;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiStrategyCaseStatusCountVO {

    private StrategyCaseStatus caseStatus;
    private long statusCount;
}
