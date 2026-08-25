package com.stockit.backend.feature.strategy.calculation.service;

import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;

/** Case의 무전략 결과와 실행 가능한 후보별 정량 평가를 한 번에 생성한다. */
public interface StrategyCandidateEvaluationService {

    StrategyCandidateEvaluationResult evaluate(
            Long strategyCaseId,
            SimulationDetailLevel detailLevel
    );
}
