package com.stockit.backend.feature.strategy.recommendation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 후보 선별, 외부 추천, 서버 의미 검증을 한 번의 순수 추천 단계로 묶는다. */
@Service
public class StrategyRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyRecommendationService.class
    );
    private static final String NO_RECOMMENDATION_CODE = "CURRENT_STATE_PREFERRED";
    private static final String NO_RECOMMENDATION_MESSAGE =
            "현재 수요예측과 재고 상태에서는 전략 적용보다 현 상태 유지가 유리합니다.";

    private final BaselineImprovementCandidateFilter baselineImprovementFilter;
    private final RecommendationCandidatePreselector preselector;
    private final AiRecommendationRequestFactory requestFactory;
    private final AiRecommendationProvider provider;
    private final StrategyRecommendationResponseValidator validator;

    public StrategyRecommendationService(
            BaselineImprovementCandidateFilter baselineImprovementFilter,
            RecommendationCandidatePreselector preselector,
            AiRecommendationRequestFactory requestFactory,
            AiRecommendationProvider provider,
            StrategyRecommendationResponseValidator validator
    ) {
        this.baselineImprovementFilter = baselineImprovementFilter;
        this.preselector = preselector;
        this.requestFactory = requestFactory;
        this.provider = provider;
        this.validator = validator;
    }

    public StrategyRecommendationResult recommend(
            Long strategyCaseId,
            StrategyCandidateEvaluationResult evaluation
    ) {
        if (strategyCaseId == null || strategyCaseId <= 0 || evaluation == null) {
            throw new IllegalArgumentException("recommendation input is invalid");
        }
        if (evaluation.evaluatedCandidates().isEmpty()) {
            log.warn(
                    "AI strategy candidate diagnostics; caseId={}, generated={}, "
                            + "simulated=0, generationExclusions={}, simulationFailures={}",
                    strategyCaseId,
                    evaluation.simulationFailures().size(),
                    evaluation.generationExclusions().size(),
                    evaluation.simulationFailures().size()
            );
            throw new IllegalArgumentException("no evaluated candidate is available");
        }
        List<StrategyCandidateEvaluationResult.EvaluatedCandidate> eligible =
                baselineImprovementFilter.filter(evaluation.evaluatedCandidates());
        int generatedCount = evaluation.evaluatedCandidates().size()
                + evaluation.simulationFailures().size();
        if (eligible.isEmpty()) {
            log.info(
                    "AI strategy candidate diagnostics; caseId={}, generated={}, "
                            + "simulated={}, eligible=0, rejectedByBaseline={}, "
                            + "generationExclusions={}, simulationFailures={}, byType={}",
                    strategyCaseId,
                    generatedCount,
                    evaluation.evaluatedCandidates().size(),
                    evaluation.evaluatedCandidates().size(),
                    evaluation.generationExclusions().size(),
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
                strategyCaseId, evaluation.baselineSimulation(), selection
        );
        AiRecommendationProviderResponse response = provider.recommend(request);
        StrategyRecommendationResult result = validator.validateAndMap(
                strategyCaseId, evaluation.calculationContext(),
                evaluation.baselineSimulation(), selection, request, response
        );
        log.info(
                "AI strategy candidate diagnostics; caseId={}, generated={}, simulated={}, "
                        + "eligible={}, rejectedByBaseline={}, preselected={}, recommended={}, "
                        + "generationExclusions={}, simulationFailures={}, byType={}",
                strategyCaseId,
                generatedCount,
                evaluation.evaluatedCandidates().size(),
                eligible.size(),
                evaluation.evaluatedCandidates().size() - eligible.size(),
                selection.candidates().size(),
                result.options().size(),
                evaluation.generationExclusions().size(),
                evaluation.simulationFailures().size(),
                countByType(evaluation.evaluatedCandidates())
        );
        return result;
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
}
