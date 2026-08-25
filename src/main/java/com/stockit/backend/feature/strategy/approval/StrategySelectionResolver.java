package com.stockit.backend.feature.strategy.approval;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy.PeriodConstraints;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResultFactory;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.StrategyCaseLifecycleGuard;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.simulation.AdjustStrategySimulationCommand;
import com.stockit.backend.feature.strategy.simulation.ResolvedStrategyAdjustment;
import com.stockit.backend.feature.strategy.simulation.StrategyAdjustmentSimulationService;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;

/** Redis 원본 또는 사용자 조정값을 DB·Teams 공통 최종 선택 객체로 확정한다. */
@Component
public class StrategySelectionResolver {

    private final StrategyResultStore resultStore;
    private final StrategySimulationContextStore contextStore;
    private final StrategyAdjustmentSimulationService adjustmentService;
    private final StrategyPeriodEligibilityPolicy periodPolicy;
    private final StrategyCaseLifecycleGuard lifecycleGuard;
    private final StrategyDateTimeProvider dateTimeProvider;
    private final StrategyAppliedQuantityCalculator quantityCalculator;
    private final StrategySelectionFingerprintFactory fingerprintFactory;

    public StrategySelectionResolver(
            StrategyResultStore resultStore,
            StrategySimulationContextStore contextStore,
            StrategyAdjustmentSimulationService adjustmentService,
            StrategyPeriodEligibilityPolicy periodPolicy,
            StrategyCaseLifecycleGuard lifecycleGuard,
            StrategyDateTimeProvider dateTimeProvider,
            StrategyAppliedQuantityCalculator quantityCalculator,
            StrategySelectionFingerprintFactory fingerprintFactory
    ) {
        this.resultStore = resultStore;
        this.contextStore = contextStore;
        this.adjustmentService = adjustmentService;
        this.periodPolicy = periodPolicy;
        this.lifecycleGuard = lifecycleGuard;
        this.dateTimeProvider = dateTimeProvider;
        this.quantityCalculator = quantityCalculator;
        this.fingerprintFactory = fingerprintFactory;
    }

    public ResolvedStrategySelection resolve(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand adjustedConditions
    ) {
        LocalDate businessDate = dateTimeProvider.now().toLocalDate();
        if (adjustedConditions != null) {
            return adjusted(strategyCaseId, candidateId, adjustedConditions, businessDate);
        }
        return original(strategyCaseId, candidateId, businessDate);
    }

    private ResolvedStrategySelection original(
            Long strategyCaseId,
            String candidateId,
            LocalDate businessDate
    ) {
        lifecycleGuard.requireSelectable(strategyCaseId);
        StrategyGenerationResult result = loadResult(strategyCaseId);
        StrategyCalculationContext context = loadContext(strategyCaseId);
        StrategyGenerationResult.Option option = requireOption(result, candidateId);
        LocalDate evaluationEnd = evaluationEnd(option, context);
        List<Long> allocatedIds = allocatedInventoryBalanceIds(option.candidate());
        periodPolicy.validateRequestedPeriod(
                context, option.candidate().startDate(), evaluationEnd, businessDate
        );
        periodPolicy.validateAllocatedPeriod(context, evaluationEnd, allocatedIds);
        PeriodConstraints constraints = periodPolicy.constraints(
                context,
                option.candidate().startDate(),
                evaluationEnd,
                allocatedIds,
                businessDate
        );
        return resolved(
                result,
                option,
                context,
                StrategySelectionInputSource.AI_RECOMMENDED,
                businessDate,
                evaluationEnd,
                constraints
        );
    }

    private ResolvedStrategySelection adjusted(
            Long strategyCaseId,
            String candidateId,
            AdjustStrategySimulationCommand command,
            LocalDate businessDate
    ) {
        ResolvedStrategyAdjustment adjustment = adjustmentService.resolveForSelection(
                strategyCaseId, candidateId, command, businessDate
        );
        StrategyGenerationResult.Candidate candidate =
                StrategyGenerationResultFactory.toCandidate(
                        adjustment.adjustedCandidate()
                );
        StrategyGenerationResult.Option original = adjustment.originalOption();
        StrategyGenerationResult.Option finalOption = new StrategyGenerationResult.Option(
                original.rank(),
                original.optionName(),
                original.recommendationReason(),
                original.advantage(),
                original.caution(),
                candidate,
                adjustment.simulation()
        );
        return resolved(
                adjustment.generationResult(),
                finalOption,
                adjustment.adjustedContext(),
                StrategySelectionInputSource.USER_SELECT,
                businessDate,
                command.endDate(),
                adjustment.periodConstraints()
        );
    }

    private ResolvedStrategySelection resolved(
            StrategyGenerationResult result,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context,
            StrategySelectionInputSource inputSource,
            LocalDate businessDate,
            LocalDate evaluationEnd,
            PeriodConstraints constraints
    ) {
        var targetQuantity = quantityCalculator.calculate(option.candidate());
        String fingerprint = fingerprintFactory.create(
                inputSource, option.candidate(), targetQuantity, evaluationEnd
        );
        return new ResolvedStrategySelection(
                result.strategyCaseId(),
                StrategyRecommendationSource.from(result),
                inputSource,
                option,
                context,
                result.baselineSimulation(),
                targetQuantity,
                businessDate,
                evaluationEnd,
                constraints,
                context.forecastMetadata().requestHash(),
                fingerprint
        );
    }

    private StrategyGenerationResult loadResult(Long strategyCaseId) {
        try {
            return resultStore.find(strategyCaseId).orElseThrow(() ->
                    new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED));
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    private StrategyCalculationContext loadContext(Long strategyCaseId) {
        try {
            return contextStore.find(strategyCaseId).orElseThrow(() ->
                    new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED));
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    private static StrategyGenerationResult.Option requireOption(
            StrategyGenerationResult result,
            String candidateId
    ) {
        return result.options().stream()
                .filter(option -> candidateId.equals(option.candidate().candidateId()))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.AI_STRATEGY_CANDIDATE_NOT_FOUND
                ));
    }

    private static LocalDate evaluationEnd(
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        if (option.candidate().endDate() != null) {
            return option.candidate().endDate();
        }
        List<com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation.DailyPoint>
                daily = option.simulation().dailySeries();
        return daily.isEmpty()
                ? context.strategyEndDate()
                : daily.get(daily.size() - 1).date();
    }

    private static List<Long> allocatedInventoryBalanceIds(
            StrategyGenerationResult.Candidate candidate
    ) {
        return candidate.actions().stream()
                .flatMap(action -> action.lotAllocations().stream())
                .map(StrategyGenerationResult.LotAllocation::inventoryBalanceId)
                .distinct()
                .toList();
    }
}
