package com.stockit.backend.feature.strategy.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

/** 후보 선별, 외부 추천, 서버 의미 검증을 한 번의 순수 추천 단계로 묶는다. */
@Service
public class StrategyRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyRecommendationService.class
    );
    private static final String NO_RECOMMENDATION_CODE = "CURRENT_STATE_PREFERRED";
    private static final String NO_RECOMMENDATION_MESSAGE =
            "현재 수요예측과 재고 상태에서는 전략 적용보다 현 상태 유지가 유리합니다.";
    private static final String NO_EXECUTABLE_STRATEGY_CODE =
            "NO_EXECUTABLE_STRATEGY";
    private static final String NO_EXECUTABLE_STRATEGY_MESSAGE =
            "현재 재고와 요청 조건에서 실행 가능한 전략을 찾지 못했습니다.";
    private static final Set<String> FALLBACK_FAILURE_CODES = Set.of(
            "LLM_RESPONSE_INVALID",
            "LLM_INTERACTION_INCOMPLETE",
            "LLM_INTERACTION_BUDGET_EXCEEDED"
    );

    private final BaselineImprovementCandidateFilter baselineImprovementFilter;
    private final RecommendationCandidatePreselector preselector;
    private final AiRecommendationRequestFactory requestFactory;
    private final AiRecommendationProvider provider;
    private final StrategyRecommendationResponseValidator validator;
    private final DeterministicRecommendationFallback fallback;

    public StrategyRecommendationService(
            BaselineImprovementCandidateFilter baselineImprovementFilter,
            RecommendationCandidatePreselector preselector,
            AiRecommendationRequestFactory requestFactory,
            AiRecommendationProvider provider,
            StrategyRecommendationResponseValidator validator,
            DeterministicRecommendationFallback fallback
    ) {
        this.baselineImprovementFilter = baselineImprovementFilter;
        this.preselector = preselector;
        this.requestFactory = requestFactory;
        this.provider = provider;
        this.validator = validator;
        this.fallback = fallback;
    }

    public StrategyRecommendationResult recommend(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0 || evaluation == null) {
            throw new IllegalArgumentException("recommendation input is invalid");
        }
        int generatedCount = evaluation.evaluatedCandidates().size()
                + evaluation.simulationFailures().size();
        if (evaluation.evaluatedCandidates().isEmpty()) {
            log.warn(
                    "AI strategy candidate diagnostics; caseId={}, generated={}, "
                            + "simulated=0, generationExclusions={}, "
                            + "generationExclusionsByReason={}, simulationFailures={}",
                    strategyCaseId,
                    generatedCount,
                    evaluation.generationExclusions().size(),
                    countExclusionsByReason(evaluation),
                    evaluation.simulationFailures().size()
            );
            if (!evaluation.simulationFailures().isEmpty()) {
                throw new IllegalArgumentException(
                        "all generated candidates failed simulation"
                );
            }
            return StrategyRecommendationResult.noRecommendation(
                    strategyCaseId,
                    evaluation.calculationContext(),
                    evaluation.baselineSimulation(),
                    NO_EXECUTABLE_STRATEGY_CODE,
                    NO_EXECUTABLE_STRATEGY_MESSAGE
            );
        }
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> eligible =
                baselineImprovementFilter.filter(evaluation.evaluatedCandidates());
        if (eligible.isEmpty()) {
            log.info(
                    "AI strategy candidate diagnostics; caseId={}, generated={}, "
                            + "simulated={}, eligible=0, rejectedByBaseline={}, "
                            + "generationExclusions={}, generationExclusionsByReason={}, "
                            + "simulationFailures={}, byType={}",
                    strategyCaseId,
                    generatedCount,
                    evaluation.evaluatedCandidates().size(),
                    evaluation.evaluatedCandidates().size(),
                    evaluation.generationExclusions().size(),
                    countExclusionsByReason(evaluation),
                    evaluation.simulationFailures().size(),
                    countByType(evaluation.evaluatedCandidates())
            );
            return StrategyRecommendationResult.noRecommendation(
                    strategyCaseId,
                    evaluation.calculationContext(),
                    evaluation.baselineSimulation(),
                    NO_RECOMMENDATION_CODE,
                    NO_RECOMMENDATION_MESSAGE
            );
        }

        StrategyCandidateEvaluationResult eligibleEvaluation =
                new StrategyCandidateEvaluationResult(
                        evaluation.calculationContext(),
                        evaluation.baselineSimulation(),
                        eligible,
                        evaluation.generationExclusions(),
                        evaluation.simulationFailures()
                );
        RecommendationCandidateSelection selection = preselector.select(
                eligibleEvaluation
        );
        AiRecommendationRequest request = requestFactory.create(
                strategyCaseId,
                evaluation.baselineSimulation(),
                selection,
                evaluation.calculationContext().requestConstraints()
        );
        StrategyRecommendationResult result = recommendAndValidate(
                strategyCaseId, evaluation, selection, request
        );
        log.info(
                "AI strategy candidate diagnostics; caseId={}, generated={}, simulated={}, "
                        + "eligible={}, rejectedByBaseline={}, preselected={}, recommended={}, "
                        + "generationExclusions={}, generationExclusionsByReason={}, "
                        + "simulationFailures={}, byType={}",
                strategyCaseId,
                generatedCount,
                evaluation.evaluatedCandidates().size(),
                eligible.size(),
                evaluation.evaluatedCandidates().size() - eligible.size(),
                selection.candidates().size(),
                result.options().size(),
                evaluation.generationExclusions().size(),
                countExclusionsByReason(evaluation),
                evaluation.simulationFailures().size(),
                countByType(evaluation.evaluatedCandidates())
        );
        return result;
    }

    private StrategyRecommendationResult recommendAndValidate(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation,
            RecommendationCandidateSelection selection,
            AiRecommendationRequest request
    ) {
        try {
            AiRecommendationProviderResponse response = provider.recommend(request);
            return validator.validateAndMap(
                    strategyCaseId, evaluation.calculationContext(),
                    evaluation.baselineSimulation(), selection, request, response
            );
        } catch (PermanentStrategyGenerationException exception) {
            if (!FALLBACK_FAILURE_CODES.contains(exception.getFailureCode())) {
                throw exception;
            }
            log.warn(
                    "LLM recommendation is unavailable; using deterministic fallback; "
                            + "caseId={}, failureCode={}, reason={}",
                    strategyCaseId, exception.getFailureCode(),
                    exception.getMessage()
            );
            AiRecommendationProviderResponse fallbackResponse = fallback.create(
                    selection, request
            );
            return validator.validateAndMap(
                    strategyCaseId, evaluation.calculationContext(),
                    evaluation.baselineSimulation(), selection, request, fallbackResponse
            );
        }
    }

    private static Map<StrategyType, Long> countByType(
            List<StrategyCandidateEvaluationResult.EvaluatedCandidate> candidates
    ) {
        return candidates.stream().collect(Collectors.groupingBy(
                value -> value.candidate().strategyTypes().get(0),
                () -> new java.util.EnumMap<>(StrategyType.class),
                Collectors.counting()
        ));
    }

    private static Map<String, Long> countExclusionsByReason(
            StrategyCandidateEvaluationResult evaluation
    ) {
        return evaluation.generationExclusions().stream().collect(
                Collectors.groupingBy(
                        exclusion -> exclusion.reason().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                )
        );
    }
}
