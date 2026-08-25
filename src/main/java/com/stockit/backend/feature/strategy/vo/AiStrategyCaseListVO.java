package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiStrategyCaseListVO {

    private Long strategyCaseId;
    private String caseName;
    private StrategyCaseStatus caseStatus;
    private StrategyGenerationStage generationStage;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Integer categoryLevel;
    private Long requesterId;
    private String requesterName;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime resultExpiresAt;
    private String failureCode;
    private String failureMessage;
}
