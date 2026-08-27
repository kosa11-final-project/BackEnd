package com.stockit.backend.feature.strategy.notification;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/** 최종 생성 상태는 저장됐지만 요청자 알림이 아직 없는 Case */
public class StrategyNotificationRecoveryCandidate {

    private Long strategyCaseId;
    private Long requesterId;
    private String caseName;
    private StrategyCaseStatus finalStatus;
    private StrategyGenerationStage generationStage;

    public Long getStrategyCaseId() {
        return strategyCaseId;
    }

    public void setStrategyCaseId(Long strategyCaseId) {
        this.strategyCaseId = strategyCaseId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public StrategyCaseStatus getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(StrategyCaseStatus finalStatus) {
        this.finalStatus = finalStatus;
    }

    public StrategyGenerationStage getGenerationStage() {
        return generationStage;
    }

    public void setGenerationStage(StrategyGenerationStage generationStage) {
        this.generationStage = generationStage;
    }
}
