package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.CandidateSimulationFailure;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics;
import com.stockit.backend.feature.strategy.domain.StrategyType;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StrategyRecommendationServiceTest {

    @Mock private BaselineImprovementCandidateFilter baselineImprovementFilter;
    @Mock private RecommendationCandidatePreselector preselector;
    @Mock private AiRecommendationRequestFactory requestFactory;
    @Mock private AiRecommendationProvider provider;
    @Mock private StrategyRecommendationResponseValidator validator;
    @Mock private DeterministicRecommendationFallback fallback;
    @Mock private AiRecommendationQualityEvaluator qualityEvaluator;

    private StrategyRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new StrategyRecommendationService(
                baselineImprovementFilter,
                preselector,
                requestFactory,
                provider,
                validator,
                fallback,
                new StrategyCandidateEvaluationClassifier(),
                qualityEvaluator,
                new AiStrategyGenerationMetrics(new SimpleMeterRegistry())
        );
        org.mockito.Mockito.lenient().when(
                qualityEvaluator.evaluate(any(), any(), any())
        ).thenReturn(
                mock(AiRecommendationQualityEvaluation.class)
        );
    }

    @Test
    void returnsCurrentStatePreferredWithoutCallingLlmWhenNoCandidateImprovesBaseline() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("LOSS");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of());

        StrategyRecommendationResult result = service.recommend(1L, evaluation);

        assertThat(result.options()).isEmpty();
        assertThat(result.noRecommendation().code())
                .isEqualTo("CURRENT_STATE_PREFERRED");
        assertThat(result.providerMetadata()).isNull();
        verify(preselector, never()).select(any());
        verify(requestFactory, never()).create(any(), any(), any(), any());
        verify(provider, never()).recommend(any());
        verify(validator, never()).validateAndMap(any(), any(), any(), any(), any(), any());
    }

    @Test
    void returnsNoExecutableStrategyAndLogsReasonsWhenNoCandidateWasGenerated(
            CapturedOutput output
    ) {
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        mock(StrategyCalculationContext.class),
                        mock(BaselineSimulation.class),
                        List.of(),
                        List.of(new CandidateExclusion(
                                StrategyType.REALLOCATION,
                                17L,
                                CandidateExclusionReason.SHARED_WAREHOUSE_NOT_FOUND,
                                "Source inventory and target do not share a warehouse"
                        )),
                        List.of()
                );

        StrategyRecommendationResult result = service.recommend(1L, evaluation);

        assertThat(result.options()).isEmpty();
        assertThat(result.noRecommendation().code())
                .isEqualTo("NO_EXECUTABLE_STRATEGY");
        assertThat(result.noRecommendation().message())
                .contains("실행 가능한 전략을 찾지 못했습니다");
        verify(baselineImprovementFilter, never()).filter(any());
        verify(provider, never()).recommend(any());
        assertThat(output).contains(
                "generationExclusionsByReason={SHARED_WAREHOUSE_NOT_FOUND=1}"
        );
    }

    @Test
    void rejectsEvaluationWhenEveryGeneratedCandidateFailedSimulation() {
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        mock(StrategyCalculationContext.class),
                        mock(BaselineSimulation.class),
                        List.of(),
                        List.of(),
                        List.of(new CandidateSimulationFailure(
                                "CAND-1", "SIMULATION_FAILED", "simulation failed"
                        ))
                );

        assertThatThrownBy(() -> service.recommend(1L, evaluation))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("STRATEGY_CANDIDATE_SIMULATION_FAILED")
                );

        verify(provider, never()).recommend(any());
    }

    @Test
    void usesDeterministicFallbackWhenLlmResponseIsInvalid() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.requestConstraints()).thenReturn(
                mock(StrategyCalculationContext.RequestConstraints.class)
        );
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("CAND-1");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(evaluated));
        AiRecommendationRequest request = mock(AiRecommendationRequest.class);
        AiRecommendationProviderResponse response =
                mock(AiRecommendationProviderResponse.class);
        AiRecommendationProviderResponse fallbackResponse =
                mock(AiRecommendationProviderResponse.class);
        StrategyRecommendationResult expected = mock(StrategyRecommendationResult.class);

        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of(evaluated));
        when(preselector.select(any())).thenReturn(selection);
        when(requestFactory.create(any(), any(), any(), any())).thenReturn(request);
        when(provider.recommend(request)).thenReturn(response);
        when(validator.validateAndMap(
                any(), any(), any(), any(), any(), any()
        )).thenThrow(new PermanentStrategyGenerationException(
                "LLM_RESPONSE_INVALID", "duplicate strategy families"
        )).thenReturn(expected);
        when(fallback.create(selection, request)).thenReturn(fallbackResponse);

        StrategyRecommendationResult result = service.recommend(1L, evaluation);

        assertThat(result).isSameAs(expected);
        verify(fallback).create(selection, request);
    }

    @Test
    void usesDeterministicFallbackWhenLlmInteractionIsIncomplete() {
        assertUsesDeterministicFallback("LLM_INTERACTION_INCOMPLETE");
    }

    @Test
    void usesDeterministicFallbackWhenLlmBudgetIsExceeded() {
        assertUsesDeterministicFallback("LLM_INTERACTION_BUDGET_EXCEEDED");
    }

    @Test
    void usesDeterministicFallbackForAuthenticationFailure() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.requestConstraints()).thenReturn(
                mock(StrategyCalculationContext.RequestConstraints.class)
        );
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("CAND-1");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(evaluated));
        AiRecommendationRequest request = mock(AiRecommendationRequest.class);
        AiRecommendationProviderResponse fallbackResponse =
                mock(AiRecommendationProviderResponse.class);
        StrategyRecommendationResult expected = mock(StrategyRecommendationResult.class);

        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of(evaluated));
        when(preselector.select(any())).thenReturn(selection);
        when(requestFactory.create(any(), any(), any(), any())).thenReturn(request);
        when(provider.recommend(request)).thenThrow(
                new PermanentStrategyGenerationException(
                        "LLM_API_AUTH_FAILED", "authentication failed"
                )
        );
        when(fallback.create(selection, request)).thenReturn(fallbackResponse);
        when(validator.validateAndMap(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(expected);

        assertThat(service.recommend(1L, evaluation)).isSameAs(expected);
        verify(fallback).create(selection, request);
    }

    @Test
    void rejectsEmptyEvaluationWhenRequiredInputIsUnavailable() {
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        mock(StrategyCalculationContext.class),
                        mock(BaselineSimulation.class),
                        List.of(),
                        List.of(new CandidateExclusion(
                                StrategyType.RT_TRANSFER,
                                17L,
                                CandidateExclusionReason.SKU_WEIGHT_NOT_FOUND,
                                "SKU weight is required"
                        )),
                        List.of()
                );

        assertThatThrownBy(() -> service.recommend(1L, evaluation))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("STRATEGY_INPUT_DATA_UNAVAILABLE")
                );
    }

    @Test
    void rejectsEmptyEvaluationWithoutAnyDiagnosticEvidence() {
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        mock(StrategyCalculationContext.class),
                        mock(BaselineSimulation.class),
                        List.of(), List.of(), List.of()
                );

        assertThatThrownBy(() -> service.recommend(1L, evaluation))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("STRATEGY_CANDIDATE_EVALUATION_INVALID")
                );
    }

    @Test
    void continuesRecommendationWhenUsableCandidateExistsWithInputExclusion() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.requestConstraints()).thenReturn(
                mock(StrategyCalculationContext.RequestConstraints.class)
        );
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("CAND-1");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context,
                        baseline,
                        List.of(evaluated),
                        List.of(new CandidateExclusion(
                                StrategyType.RT_TRANSFER,
                                17L,
                                CandidateExclusionReason.SKU_WEIGHT_NOT_FOUND,
                                "SKU weight is required"
                        )),
                        List.of()
                );
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(evaluated));
        AiRecommendationRequest request = mock(AiRecommendationRequest.class);
        AiRecommendationProviderResponse providerResponse =
                mock(AiRecommendationProviderResponse.class);
        StrategyRecommendationResult expected = mock(StrategyRecommendationResult.class);
        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of(evaluated));
        when(preselector.select(any())).thenReturn(selection);
        when(requestFactory.create(any(), any(), any(), any())).thenReturn(request);
        when(provider.recommend(request)).thenReturn(providerResponse);
        when(validator.validateAndMap(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(expected);

        assertThat(service.recommend(1L, evaluation)).isSameAs(expected);
        verify(provider).recommend(request);
    }

    @Test
    void retriesTransientLlmFailureBeforeFinalAttempt() {
        RecommendationFixture fixture = recommendationFixture();
        when(provider.recommend(fixture.request())).thenThrow(
                new RetryableStrategyGenerationException(
                        "LLM_API_RATE_LIMITED",
                        com.stockit.backend.feature.strategy.domain.StrategyGenerationStage
                                .STRATEGY_GENERATING,
                        "rate limited"
                )
        );

        assertThatThrownBy(() -> service.recommend(
                1L,
                fixture.evaluation(),
                RecommendationExecutionPolicy.retryTransientLlmFailure()
        )).isInstanceOf(RetryableStrategyGenerationException.class);
        verify(fallback, never()).create(any(), any());
    }

    @Test
    void usesDeterministicFallbackForTransientLlmFailureOnFinalAttempt() {
        RecommendationFixture fixture = recommendationFixture();
        AiRecommendationProviderResponse fallbackResponse =
                mock(AiRecommendationProviderResponse.class);
        StrategyRecommendationResult expected = mock(StrategyRecommendationResult.class);
        when(provider.recommend(fixture.request())).thenThrow(
                new RetryableStrategyGenerationException(
                        "LLM_API_RATE_LIMITED",
                        com.stockit.backend.feature.strategy.domain.StrategyGenerationStage
                                .STRATEGY_GENERATING,
                        "rate limited"
                )
        );
        when(fallback.create(fixture.selection(), fixture.request()))
                .thenReturn(fallbackResponse);
        when(validator.validateAndMap(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(expected);

        StrategyRecommendationResult result = service.recommend(
                1L,
                fixture.evaluation(),
                RecommendationExecutionPolicy.fallbackTransientLlmFailure()
        );

        assertThat(result).isSameAs(expected);
        verify(fallback).create(fixture.selection(), fixture.request());
    }

    private void assertUsesDeterministicFallback(String failureCode) {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.requestConstraints()).thenReturn(
                mock(StrategyCalculationContext.RequestConstraints.class)
        );
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("CAND-1");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(evaluated));
        AiRecommendationRequest request = mock(AiRecommendationRequest.class);
        AiRecommendationProviderResponse fallbackResponse =
                mock(AiRecommendationProviderResponse.class);
        StrategyRecommendationResult expected = mock(StrategyRecommendationResult.class);

        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of(evaluated));
        when(preselector.select(any())).thenReturn(selection);
        when(requestFactory.create(any(), any(), any(), any())).thenReturn(request);
        when(provider.recommend(request)).thenThrow(
                new PermanentStrategyGenerationException(failureCode, "LLM unavailable")
        );
        when(fallback.create(selection, request)).thenReturn(fallbackResponse);
        when(validator.validateAndMap(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(expected);

        StrategyRecommendationResult result = service.recommend(1L, evaluation);

        assertThat(result).isSameAs(expected);
        verify(fallback).create(selection, request);
    }

    private RecommendationFixture recommendationFixture() {
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        when(context.requestConstraints()).thenReturn(
                mock(StrategyCalculationContext.RequestConstraints.class)
        );
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        var evaluated = evaluated("CAND-1");
        StrategyCandidateEvaluationResult evaluation =
                new StrategyCandidateEvaluationResult(
                        context, baseline, List.of(evaluated), List.of(), List.of()
                );
        RecommendationCandidateSelection selection =
                new RecommendationCandidateSelection(List.of(evaluated));
        AiRecommendationRequest request = mock(AiRecommendationRequest.class);
        when(baselineImprovementFilter.filter(evaluation.evaluatedCandidates()))
                .thenReturn(List.of(evaluated));
        when(preselector.select(any())).thenReturn(selection);
        when(requestFactory.create(any(), any(), any(), any())).thenReturn(request);
        return new RecommendationFixture(evaluation, selection, request);
    }

    private record RecommendationFixture(
            StrategyCandidateEvaluationResult evaluation,
            RecommendationCandidateSelection selection,
            AiRecommendationRequest request
    ) {}

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id
    ) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(candidate.strategyTypes()).thenReturn(
                List.of(com.stockit.backend.feature.strategy.domain.StrategyType.PRICE_DISCOUNT)
        );
        when(simulation.candidateId()).thenReturn(id);
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate, simulation
        );
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate
            evaluatedWithoutStrategyType(String id) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        when(simulation.candidateId()).thenReturn(id);
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate, simulation
        );
    }
}
