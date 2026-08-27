package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.domain.StrategyRetryDateAdjustmentPolicy;
import com.stockit.backend.feature.strategy.dto.response.RetryAiStrategyGenerationResponse;

/** 최종 실패한 AI 전략 생성 Case를 새로운 실행 단위로 재요청한다. */
public interface AiStrategyGenerationRetryService {

    RetryAiStrategyGenerationResponse retry(
            Long failedStrategyCaseId,
            StrategyRetryDateAdjustmentPolicy dateAdjustmentPolicy,
            Long requesterId
    );
}
