package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;

import com.stockit.backend.common.persistence.BaseEntity;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code strategy_case} 테이블과 매핑되는 AI 전략 생성 요청 영속 객체
 */
@Getter
@Setter
public class StrategyCaseVO extends BaseEntity {

    private Long strategyCaseId;
    private Long skuId;
    private Long requestedSalesPointId;
    private String caseCode;
    private String caseName;
    private StrategyCaseStatus caseStatus;
    private StrategyGenerationStage generationStage;
    private String requestPayloadJson;
    private String resultCacheKey;
    private LocalDateTime resultExpiresAt;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime completedAt;

    /**
     * 비동기 전략 생성을 시작하기 전 최초 저장 상태 생성
     */
    public static StrategyCaseVO generating(
            Long skuId,
            Long requestedSalesPointId,
            String caseCode,
            String caseName,
            String requestPayloadJson,
            Long requesterId
    ) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setSkuId(skuId);
        strategyCase.setRequestedSalesPointId(requestedSalesPointId);
        strategyCase.setCaseCode(caseCode);
        strategyCase.setCaseName(caseName);
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setRequestPayloadJson(requestPayloadJson);
        strategyCase.setCreatedBy(requesterId);
        strategyCase.setUpdatedBy(requesterId);
        return strategyCase;
    }
}
