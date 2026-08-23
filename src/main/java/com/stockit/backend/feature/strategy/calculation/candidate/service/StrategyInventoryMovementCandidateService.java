package com.stockit.backend.feature.strategy.calculation.candidate.service;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;

/** Case ID로 계산 컨텍스트를 구성하고 현재 구현된 이동 후보를 생성하는 진입점. */
public interface StrategyInventoryMovementCandidateService {

    CandidateGenerationResult generate(Long strategyCaseId);
}
