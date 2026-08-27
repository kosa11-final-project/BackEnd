package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;

import com.stockit.backend.common.persistence.BaseEntity;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code strategy_case} 테이블과 매핑되는 AI 전략 생성 요청 영속 객체
 */
@Getter
@Setter
public class StrategyCaseVO extends BaseEntity {

    private Long strategyCaseId;
    private Long retryParentCaseId;
    private Long skuId;
    private Long requestedSalesPointId;
    private String caseCode;
    private String caseName;
    private StrategyCaseStatus caseStatus;
    private StrategyGenerationStage generationStage;
    private StrategyRecommendationOutcome recommendationOutcome;
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
        return generating(
                skuId,
                requestedSalesPointId,
                caseCode,
                caseName,
                requestPayloadJson,
                requesterId,
                null
        );
    }

    /** 사용자 재시도 관계를 포함한 신규 생성 상태를 만든다. */
    public static StrategyCaseVO generating(
            Long skuId,
            Long requestedSalesPointId,
            String caseCode,
            String caseName,
            String requestPayloadJson,
            Long requesterId,
            Long retryParentCaseId
    ) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setRetryParentCaseId(retryParentCaseId);
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
