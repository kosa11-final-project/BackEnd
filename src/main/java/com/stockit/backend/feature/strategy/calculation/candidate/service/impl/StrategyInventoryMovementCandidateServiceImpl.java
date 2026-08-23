package com.stockit.backend.feature.strategy.calculation.candidate.service.impl;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyCandidateGenerationService;
import com.stockit.backend.feature.strategy.calculation.candidate.service.StrategyInventoryMovementCandidateService;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCalculationContextLoader;

@Service
public class StrategyInventoryMovementCandidateServiceImpl
        implements StrategyInventoryMovementCandidateService {

    private final StrategyCalculationContextLoader contextLoader;
    private final StrategyCandidateGenerationService generationService;

    public StrategyInventoryMovementCandidateServiceImpl(
            StrategyCalculationContextLoader contextLoader,
            StrategyCandidateGenerationService generationService
    ) {
        this.contextLoader = contextLoader;
        this.generationService = generationService;
    }

    @Override
    public CandidateGenerationResult generate(Long strategyCaseId) {
        StrategyCalculationContext context = contextLoader.load(strategyCaseId);
        return generationService.generate(context);
    }
}
