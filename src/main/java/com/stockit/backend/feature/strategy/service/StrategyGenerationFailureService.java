package com.stockit.backend.feature.strategy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/**
 * 이미 커밋된 생성 Case에 최종 실패 상태를 독립적으로 기록
 */
@Service
public class StrategyGenerationFailureService {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 2000;

    private final StrategyCaseMapper strategyCaseMapper;

    public StrategyGenerationFailureService(StrategyCaseMapper strategyCaseMapper) {
        this.strategyCaseMapper = strategyCaseMapper;
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
        if (expectedStage == null) {
            return strategyCaseMapper.markGenerationFailedIfGenerating(
                    strategyCaseId,
                    failureCode,
                    normalizedMessage
            ) == 1;
        }
        // 늦게 도착한 실패가 이미 다음 단계로 진행된 Case를 덮어쓰지 않도록 단계 비교
        return strategyCaseMapper.markGenerationFailedAtStage(
                strategyCaseId,
                expectedStage,
                failureCode,
                normalizedMessage
        ) == 1;
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
