package com.stockit.backend.feature.strategy.calculation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.engine.BaselineSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.service.StrategyBaselineSimulationService;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCalculationContextLoader;

/** MQ와 분리된 기준 시뮬레이션 애플리케이션 서비스. */
@Service
public class StrategyBaselineSimulationServiceImpl
        implements StrategyBaselineSimulationService {

    private final StrategyCalculationContextLoader contextLoader;
    private final BaselineSimulationEngine simulationEngine;

    public StrategyBaselineSimulationServiceImpl(
            StrategyCalculationContextLoader contextLoader,
            BaselineSimulationEngine simulationEngine
    ) {
        this.contextLoader = contextLoader;
        this.simulationEngine = simulationEngine;
    }

    @Override
    @Transactional(readOnly = true)
    public BaselineSimulation simulate(Long strategyCaseId) {
        StrategyCalculationContext context = contextLoader.load(strategyCaseId);
        return simulationEngine.simulate(context);
    }
}
