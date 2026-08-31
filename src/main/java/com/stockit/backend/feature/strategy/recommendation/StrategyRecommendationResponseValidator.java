package com.stockit.backend.feature.strategy.recommendation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

/** JSON Schema만으로 보장할 수 없는 후보 소속·중복·순위·문자열 규칙을 검증한다. */
@Component
public class StrategyRecommendationResponseValidator {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final Pattern DATE_IN_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern QUANTITY_IN_NAME = Pattern.compile("\\d+(?:\\.\\d+)?\\s*개");

    public StrategyRecommendationResult validateAndMap(
            Long strategyCaseId,
            StrategyCalculationContext calculationContext,
            BaselineSimulation baseline,
            RecommendationCandidateSelection selection,
            AiRecommendationRequest request,
            AiRecommendationProviderResponse response
    ) {
        if (response == null || response.recommendations() == null) {
            fail("LLM response is empty");
        }
        List<AiRecommendationProviderResponse.Recommendation> values =
                response.recommendations();
        if (values.size() < request.minimumRecommendationCount()
                || values.size() > request.maximumRecommendationCount()) {
            fail("LLM recommendation count is outside the allowed range");
        }

        Map<String, StrategyCandidateEvaluationResult.EvaluatedCandidate> allowed =
                selection.candidates().stream().collect(Collectors.toMap(
                        value -> value.candidate().candidateId(), Function.identity()
                ));
        Set<String> ids = new HashSet<>();
        Set<RecommendationFamilyKey> familyKeys = new HashSet<>();
        Set<StrategyType> selectedTypes = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (AiRecommendationProviderResponse.Recommendation value : values) {
            if (value == null || !allowed.containsKey(value.candidateId())
                    || !ids.add(value.candidateId()) || !ranks.add(value.rank())
                    || value.rank() < 1 || value.rank() > values.size()) {
                fail("LLM response contains an invalid candidate or rank");
            }
            RecommendationFamilyKey familyKey = RecommendationFamilyKey.from(
                    allowed.get(value.candidateId()).candidate()
            );
            if (!familyKeys.add(familyKey)) {
                fail("LLM response contains duplicate strategy families");
            }
            requireOptionName(value.optionName());
            selectedTypes.add(primaryType(allowed.get(value.candidateId())));
            requireText(value.recommendationReason(), MAX_TEXT_LENGTH,
                    "recommendationReason");
            requireText(value.advantage(), MAX_TEXT_LENGTH, "advantage");
            requireText(value.caution(), MAX_TEXT_LENGTH, "caution");
        }
        for (int rank = 1; rank <= values.size(); rank++) {
            if (!ranks.contains(rank)) {
                fail("LLM ranks must be contiguous from one");
            }
        }
        long availableTypeCount = selection.candidates().stream()
                .map(StrategyRecommendationResponseValidator::primaryType)
                .distinct()
                .count();
        int requiredTypeCount = (int) Math.min(values.size(), availableTypeCount);
        if (selectedTypes.size() < requiredTypeCount) {
            fail("LLM response does not maximize strategy type diversity");
        }

        List<StrategyRecommendationResult.RecommendedOption> options = values.stream()
                .sorted(java.util.Comparator.comparingInt(
                        AiRecommendationProviderResponse.Recommendation::rank))
                .map(value -> new StrategyRecommendationResult.RecommendedOption(
                        value.rank(), value.optionName(), value.recommendationReason(),
                        value.advantage(), value.caution(), allowed.get(value.candidateId())
                )).toList();
        return new StrategyRecommendationResult(
                strategyCaseId, calculationContext, baseline, options,
                null,
                new StrategyRecommendationResult.ProviderMetadata(
                        response.interactionId(), response.model(), response.inputTokens(),
                        response.outputTokens()
                )
        );
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            fail(field + " is invalid");
        }
    }

    private static void requireOptionName(String value) {
        requireText(value, MAX_NAME_LENGTH, "optionName");
        if (DATE_IN_NAME.matcher(value).find() || QUANTITY_IN_NAME.matcher(value).find()) {
            fail("optionName must not contain a date or quantity");
        }
    }

    private static StrategyType primaryType(
            StrategyCandidateEvaluationResult.EvaluatedCandidate value
    ) {
        return value.candidate().strategyTypes().get(0);
    }

    private static void fail(String message) {
        throw new PermanentStrategyGenerationException(
                "LLM_RESPONSE_INVALID",
                StrategyGenerationStage.STRATEGY_GENERATING,
                message
        );
    }
}
