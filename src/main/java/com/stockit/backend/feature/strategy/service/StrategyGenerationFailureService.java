package com.stockit.backend.feature.strategy.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

/**
 * 이미 커밋된 생성 Case에 최종 실패 상태를 독립적으로 기록
 */
@Service
public class StrategyGenerationFailureService {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 2000;

    private final StrategyCaseMapper strategyCaseMapper;
    private final ApplicationEventPublisher eventPublisher;

    public StrategyGenerationFailureService(
            StrategyCaseMapper strategyCaseMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(
            Long strategyCaseId,
            String failureCode,
            String failureMessage
    ) {
        return markFailed(strategyCaseId, null, failureCode, failureMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(
            Long strategyCaseId,
            StrategyGenerationStage expectedStage,
            String failureCode,
            String failureMessage
    ) {
        String normalizedMessage = normalizeMessage(failureMessage);
        boolean updated;
        if (expectedStage == null) {
            updated = strategyCaseMapper.markGenerationFailedIfGenerating(
                    strategyCaseId,
                    failureCode,
                    normalizedMessage
            ) == 1;
        } else {
            // 늦게 도착한 실패가 이미 다음 단계로 진행된 Case를 덮어쓰지 않도록 단계 비교
            updated = strategyCaseMapper.markGenerationFailedAtStage(
                    strategyCaseId,
                    expectedStage,
                    failureCode,
                    normalizedMessage
            ) == 1;
        }
        if (updated) {
            eventPublisher.publishEvent(new StrategyGenerationStateChangedEvent(
                    strategyCaseId,
                    StrategyCaseStatus.GENERATION_FAILED,
                    expectedStage
            ));
        }
        return updated;
    }

    private static String normalizeMessage(String failureMessage) {
        if (failureMessage == null || failureMessage.isBlank()) {
            return "Unknown AI strategy generation failure";
        }
        String trimmed = failureMessage.trim();
        if (trimmed.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
