package com.stockit.backend.feature.strategy.result;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

/** 추천된 3~4개 후보만 일별 시뮬레이션으로 확장해 최종 캐시 결과를 만든다. */
@Component
public class StrategyGenerationResultFactory {

    private final StrategyCandidateSimulationEngine simulationEngine;
    private final StrategyDateTimeProvider dateTimeProvider;

    public StrategyGenerationResultFactory(
            StrategyCandidateSimulationEngine simulationEngine,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.simulationEngine = simulationEngine;
        this.dateTimeProvider = dateTimeProvider;
    }

    public StrategyGenerationResult create(
            Long strategyCaseId,
            StrategyRecommendationResult recommendation
    ) {
        StrategyCalculationContext context = recommendation.calculationContext();
        List<StrategyGenerationResult.Option> options = recommendation.options().stream()
                .map(option -> toFinalOption(context, recommendation, option))
                .toList();
        LocalDateTime generatedAt = dateTimeProvider.now();
        if (recommendation.noRecommendation() != null) {
            StrategyRecommendationResult.NoRecommendation reason =
                    recommendation.noRecommendation();
            return new StrategyGenerationResult(
                    StrategyGenerationResult.CURRENT_SCHEMA_VERSION,
                    strategyCaseId,
                    generatedAt,
                    recommendation.baselineSimulation(),
                    List.of(),
                    new StrategyGenerationResult.NoRecommendation(
                            reason.code(), reason.message()
                    ),
                    null
            );
        }
        StrategyRecommendationResult.ProviderMetadata metadata =
                recommendation.providerMetadata();
        return new StrategyGenerationResult(
                StrategyGenerationResult.CURRENT_SCHEMA_VERSION,
                strategyCaseId,
                generatedAt,
                recommendation.baselineSimulation(),
                options,
                null,
                new StrategyGenerationResult.ProviderMetadata(
                        metadata.interactionId(), metadata.model(), metadata.inputTokens(),
                        metadata.outputTokens()
                )
        );
    }

    private StrategyGenerationResult.Option toFinalOption(
            StrategyCalculationContext context,
            StrategyRecommendationResult recommendation,
            StrategyRecommendationResult.RecommendedOption option
    ) {
        StrategyCandidate candidate = option.evaluatedCandidate().candidate();
        try {
            StrategyCandidateSimulation simulation = simulationEngine.simulate(
                    context,
                    candidate,
                    recommendation.baselineSimulation(),
                    SimulationDetailLevel.WITH_DAILY_SERIES
            );
            return new StrategyGenerationResult.Option(
                    option.rank(), option.optionName(), option.recommendationReason(),
                    option.advantage(), option.caution(), toCandidate(candidate), simulation
            );
        } catch (CandidateSimulationException exception) {
            throw new PermanentStrategyGenerationException(
                    "FINAL_SIMULATION_FAILED",
                    StrategyGenerationStage.STRATEGY_GENERATING,
                    "Recommended candidate daily simulation failed: "
                            + candidate.candidateId(),
                    exception
            );
        }
    }

    private static StrategyGenerationResult.Candidate toCandidate(
            StrategyCandidate candidate
    ) {
        return new StrategyGenerationResult.Candidate(
                candidate.candidateId(), candidate.strategyTypes(), candidate.startDate(),
                candidate.endDate(), candidate.actions().stream().map(action ->
                        new StrategyGenerationResult.Action(
                                action.actionType(), action.source().warehouseId(),
                                action.source().salesPointId(), action.target().warehouseId(),
                                action.target().salesPointId(), action.actionQuantity(),
                                action.estimatedActionCost(), action.strategyPrice(),
                                action.discountRate(), action.lotAllocations().stream().map(lot ->
                                new StrategyGenerationResult.LotAllocation(
                                        lot.inventoryBalanceId(), lot.lotId(), lot.quantity(),
                                        lot.priorityNo()
                                )).toList()
                        )).toList(),
                candidate.assumptions(),
                new StrategyGenerationResult.Preference(
                        candidate.preference().strategyPriority(),
                        candidate.preference().targetPriority(),
                        candidate.preference().quantityPercentage()
                ),
                candidate.evidence().maxExecutableQty()
        );
    }
}
