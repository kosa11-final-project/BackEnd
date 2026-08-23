package com.stockit.backend.feature.strategy.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionBaseVO {
    private Long finalSelectionId;
    private Long strategyCaseId;
    private Long strategyOptionId;
    private String caseCode;
    private String caseName;
    private String caseStatus;
    private LocalDateTime establishedAt;
    private LocalDateTime lastSyncedAt;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String unitCode;
    private String productName;
    private String imageUrl;
    private String optionName;
    private String recommendationReason;
    private String resultStatus;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private BigDecimal goalTargetValue;
    private BigDecimal goalActualValue;
    private BigDecimal achievementRate;
}
