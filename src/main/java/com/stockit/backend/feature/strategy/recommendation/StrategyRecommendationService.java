package com.stockit.backend.feature.strategy.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics;
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics.Stage;

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
    private static final String LLM_FAILURE_PREFIX = "LLM_";
    private static final StrategyGenerationStage STAGE =
            StrategyGenerationStage.STRATEGY_GENERATING;

    private final BaselineImprovementCandidateFilter baselineImprovementFilter;
    private final RecommendationCandidatePreselector preselector;
    private final AiRecommendationRequestFactory requestFactory;
    private final AiRecommendationProvider provider;
    private final StrategyRecommendationResponseValidator validator;
    private final DeterministicRecommendationFallback fallback;
    private final StrategyCandidateEvaluationClassifier evaluationClassifier;
    private final AiRecommendationQualityEvaluator qualityEvaluator;
    private final AiStrategyGenerationMetrics metrics;

    public StrategyRecommendationService(
            BaselineImprovementCandidateFilter baselineImprovementFilter,
            RecommendationCandidatePreselector preselector,
            AiRecommendationRequestFactory requestFactory,
            AiRecommendationProvider provider,
            StrategyRecommendationResponseValidator validator,
            DeterministicRecommendationFallback fallback,
            StrategyCandidateEvaluationClassifier evaluationClassifier,
            AiRecommendationQualityEvaluator qualityEvaluator,
            AiStrategyGenerationMetrics metrics
    ) {
        this.baselineImprovementFilter = baselineImprovementFilter;
        this.preselector = preselector;
        this.requestFactory = requestFactory;
        this.provider = provider;
        this.validator = validator;
        this.fallback = fallback;
        this.evaluationClassifier = evaluationClassifier;
        this.qualityEvaluator = qualityEvaluator;
        this.metrics = metrics;
    }

    public StrategyRecommendationResult recommend(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation
    ) {
        return recommend(
                strategyCaseId,
                evaluation,
                RecommendationExecutionPolicy.retryTransientLlmFailure()
        );
    }

    public StrategyRecommendationResult recommend(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation,
            RecommendationExecutionPolicy executionPolicy
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0 || evaluation == null) {
            throw new IllegalArgumentException("recommendation input is invalid");
        }
        if (executionPolicy == null) {
            throw new IllegalArgumentException("recommendation execution policy is required");
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
            StrategyRecommendationResult result = handleEmptyEvaluation(
                    strategyCaseId, evaluation
            );
            metrics.recordRecommendation(result);
            return result;
        }
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> eligible =
                baselineImprovementFilter.filter(evaluation.evaluatedCandidates());
        metrics.recordCandidateCount("eligible", eligible.size());
        metrics.recordCandidateCount(
                "baseline_rejected",
                evaluation.evaluatedCandidates().size() - eligible.size()
        );
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
            StrategyRecommendationResult result = StrategyRecommendationResult.noRecommendation(
                    strategyCaseId,
                    evaluation.calculationContext(),
                    evaluation.baselineSimulation(),
                    NO_RECOMMENDATION_CODE,
                    NO_RECOMMENDATION_MESSAGE
            );
            metrics.recordRecommendation(result);
            return result;
        }

        StrategyCandidateEvaluationResult eligibleEvaluation =
                new StrategyCandidateEvaluationResult(
                        evaluation.calculationContext(),
                        evaluation.baselineSimulation(),
                        eligible,
                        evaluation.generationExclusions(),
                        evaluation.simulationFailures()
                );
        RecommendationCandidateSelection selection = metrics.measure(
                Stage.CANDIDATE_PRESELECTION,
                () -> preselector.select(eligibleEvaluation)
        );
        metrics.recordCandidateCount("preselected", selection.candidates().size());
        AiRecommendationRequest request = requestFactory.create(
                strategyCaseId,
                evaluation.baselineSimulation(),
                selection,
                evaluation.calculationContext().requestConstraints()
        );
        StrategyRecommendationResult result = recommendAndValidate(
                strategyCaseId, evaluation, selection, request, executionPolicy
        );
        metrics.recordRecommendation(result);
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
            AiRecommendationRequest request,
            RecommendationExecutionPolicy executionPolicy
    ) {
        try {
            AiRecommendationProviderResponse response = metrics.measure(
                    Stage.LLM_RECOMMENDATION,
                    () -> provider.recommend(request)
            );
            metrics.recordLlmUsage(response);
            StrategyRecommendationResult result = validator.validateAndMap(
                    strategyCaseId, evaluation.calculationContext(),
                    evaluation.baselineSimulation(), selection, request, response
            );
            metrics.recordRecommendationQuality(
                    qualityEvaluator.evaluate(
                            request, response,
                            evaluation.calculationContext().requestConstraints()
                    ), "llm"
            );
            return result;
        } catch (PermanentStrategyGenerationException exception) {
            if (!isLlmFailure(exception.getFailureCode())) {
                throw exception;
            }
            metrics.recordLlmFailure(exception.getFailureCode());
            return fallback(
                    strategyCaseId, evaluation, selection, request,
                    exception.getFailureCode(), exception.getMessage()
            );
        } catch (RetryableStrategyGenerationException exception) {
            if (!executionPolicy.fallbackOnTransientLlmFailure()
                    || !isLlmFailure(exception.getFailureCode())) {
                if (isLlmFailure(exception.getFailureCode())) {
                    metrics.recordLlmFailure(exception.getFailureCode());
                }
                throw exception;
            }
            metrics.recordLlmFailure(exception.getFailureCode());
            return fallback(
                    strategyCaseId, evaluation, selection, request,
                    exception.getFailureCode(), exception.getMessage()
            );
        }
    }

    private StrategyRecommendationResult handleEmptyEvaluation(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation
    ) {
        return switch (evaluationClassifier.classify(evaluation)) {
            case NO_EXECUTABLE_STRATEGY ->
                    StrategyRecommendationResult.noRecommendation(
                            strategyCaseId,
                            evaluation.calculationContext(),
                            evaluation.baselineSimulation(),
                            NO_EXECUTABLE_STRATEGY_CODE,
                            NO_EXECUTABLE_STRATEGY_MESSAGE
                    );
            case INPUT_DATA_UNAVAILABLE -> throw new PermanentStrategyGenerationException(
                    "STRATEGY_INPUT_DATA_UNAVAILABLE",
                    STAGE,
                    "전략 계산에 필요한 상품·판매처·물류 기준 정보가 부족합니다."
            );
            case SIMULATION_FAILED -> throw new PermanentStrategyGenerationException(
                    "STRATEGY_CANDIDATE_SIMULATION_FAILED",
                    STAGE,
                    "생성된 전략 후보의 시뮬레이션을 완료하지 못했습니다."
            );
            case INVALID_EVALUATION_RESULT ->
                    throw new PermanentStrategyGenerationException(
                            "STRATEGY_CANDIDATE_EVALUATION_INVALID",
                            STAGE,
                            "전략 후보 평가 결과가 올바르지 않습니다."
                    );
            case RECOMMENDABLE -> throw new IllegalStateException(
                    "empty evaluation cannot be recommendable"
            );
        };
    }

    private StrategyRecommendationResult fallback(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation,
            RecommendationCandidateSelection selection,
            AiRecommendationRequest request,
            String failureCode,
            String failureMessage
    ) {
        log.warn(
                "LLM recommendation is unavailable; using deterministic fallback; "
                        + "caseId={}, failureCode={}, reason={}",
                strategyCaseId, failureCode, failureMessage
        );
        AiRecommendationProviderResponse fallbackResponse = fallback.create(
                selection, request
        );
        StrategyRecommendationResult result = validator.validateAndMap(
                strategyCaseId, evaluation.calculationContext(),
                evaluation.baselineSimulation(), selection, request, fallbackResponse
        );
        metrics.recordRecommendationQuality(
                qualityEvaluator.evaluate(
                        request, fallbackResponse,
                        evaluation.calculationContext().requestConstraints()
                ), "fallback"
        );
        return result;
    }

    private static boolean isLlmFailure(String failureCode) {
        return failureCode != null && failureCode.startsWith(LLM_FAILURE_PREFIX);
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
