package com.stockit.backend.feature.strategy.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/** Redis와 생성 상세 API가 공유하는 최종 AI 전략 결과 스냅샷. */
public record StrategyGenerationResult(
        int schemaVersion,
        Long strategyCaseId,
        LocalDateTime generatedAt,
        BaselineSimulation baselineSimulation,
        List<Option> options,
        NoRecommendation noRecommendation,
        ProviderMetadata providerMetadata
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public StrategyGenerationResult {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || strategyCaseId == null
                || generatedAt == null || baselineSimulation == null
                || options == null) {
            throw new IllegalArgumentException("strategy generation result is invalid");
        }
        options = List.copyOf(options);
        validateOptions(options);
        boolean recommended = !options.isEmpty();
        if (recommended == (noRecommendation != null)
                || recommended != (providerMetadata != null)) {
            throw new IllegalArgumentException("strategy generation outcome is invalid");
        }
    }

    private static void validateOptions(List<Option> options) {
        Set<String> candidateIds = new HashSet<>();
        for (Option option : options) {
            if (option == null || option.candidate() == null
                    || option.simulation() == null) {
                throw new IllegalArgumentException(
                        "strategy generation option is invalid"
                );
            }
            Candidate candidate = option.candidate();
            if (candidate.candidateId() == null
                    || candidate.candidateId().isBlank()
                    || !candidateIds.add(candidate.candidateId())
                    || candidate.startDate() == null
                    || (candidate.endDate() != null
                    && candidate.startDate().isAfter(candidate.endDate()))
                    || !candidate.candidateId().equals(
                            option.simulation().candidateId()
                    )) {
                throw new IllegalArgumentException(
                        "strategy generation candidate is invalid"
                );
            }
        }
    }

    public record Option(
            int rank,
            String optionName,
            String recommendationReason,
            String advantage,
            String caution,
            Candidate candidate,
            StrategyCandidateSimulation simulation
    ) {
    }

    public record Candidate(
            String candidateId,
            List<StrategyType> strategyTypes,
            LocalDate startDate,
            LocalDate endDate,
            List<Action> actions,
            List<CandidateAssumption> assumptions,
            Preference preference,
            BigDecimal maxExecutableQty
    ) {
        public Candidate {
            strategyTypes = List.copyOf(strategyTypes);
            actions = List.copyOf(actions);
            assumptions = List.copyOf(assumptions);
        }
    }

    public record Action(
            StrategyType actionType,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long targetWarehouseId,
            Long targetSalesPointId,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            BigDecimal strategyPrice,
            BigDecimal discountRate,
            List<LotAllocation> lotAllocations
    ) {
        public Action {
            lotAllocations = List.copyOf(lotAllocations);
        }
    }

    public record LotAllocation(
            Long inventoryBalanceId,
            Long lotId,
            BigDecimal quantity,
            int priorityNo
    ) {
    }

    public record Preference(
            int strategyPriority,
            int targetPriority,
            int quantityPercentage
    ) {
    }

    public record ProviderMetadata(
            String interactionId,
            String model,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }

    public record NoRecommendation(
            String code,
            String message
    ) {
        public NoRecommendation {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("no recommendation reason is invalid");
            }
        }
    }
}
