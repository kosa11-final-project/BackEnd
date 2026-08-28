package com.stockit.backend.feature.strategy.service.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCandidateEvaluationService;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationBusyException;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationService;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResultFactory;
import com.stockit.backend.feature.strategy.result.StrategyResultCacheEntry;
import com.stockit.backend.feature.strategy.result.StrategyResultLock;
import com.stockit.backend.feature.strategy.result.StrategyResultLockManager;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.StrategyGenerationStageService;
import com.stockit.backend.feature.strategy.service.StrategyRecommendationStageProcessor;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/** STRATEGY_GENERATING 단계의 계산·LLM·최종 캐시·DB 완료 전환을 복구 가능하게 처리한다. */
@Service
public class StrategyRecommendationStageProcessorImpl
        implements StrategyRecommendationStageProcessor {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyRecommendationStageProcessorImpl.class
    );
    private static final StrategyGenerationStage STAGE =
            StrategyGenerationStage.STRATEGY_GENERATING;

    private final StrategyCaseMapper caseMapper;
    private final StrategyCandidateEvaluationService evaluationService;
    private final StrategyRecommendationService recommendationService;
    private final StrategyGenerationResultFactory resultFactory;
    private final StrategyResultStore resultStore;
    private final StrategyResultLockManager lockManager;
    private final StrategyGenerationStageService stageService;
    private final StrategyResultProperties resultProperties;
    private final StrategySimulationContextStore simulationContextStore;

    public StrategyRecommendationStageProcessorImpl(
            StrategyCaseMapper caseMapper,
            StrategyCandidateEvaluationService evaluationService,
            StrategyRecommendationService recommendationService,
            StrategyGenerationResultFactory resultFactory,
            StrategyResultStore resultStore,
            StrategyResultLockManager lockManager,
            StrategyGenerationStageService stageService,
            StrategyResultProperties resultProperties,
            StrategySimulationContextStore simulationContextStore
    ) {
        this.caseMapper = caseMapper;
        this.evaluationService = evaluationService;
        this.recommendationService = recommendationService;
        this.resultFactory = resultFactory;
        this.resultStore = resultStore;
        this.lockManager = lockManager;
        this.stageService = stageService;
        this.resultProperties = resultProperties;
        this.simulationContextStore = simulationContextStore;
    }

    @Override
    public void process(Long strategyCaseId) {
        StrategyCaseVO strategyCase = loadCase(strategyCaseId);
        if (isCompleteOrTerminal(strategyCase)) {
            log.debug(
                    "Strategy recommendation stage skipped because case is already "
                            + "complete or terminal. strategyCaseId={}, caseStatus={}, "
                            + "generationStage={}",
                    strategyCaseId, strategyCase.getCaseStatus(),
                    strategyCase.getGenerationStage()
            );
            return;
        }
        requireStrategyGenerating(strategyCase);
        log.debug(
                "Strategy recommendation stage started. strategyCaseId={}",
                strategyCaseId
        );

        Optional<StrategyGenerationResult> cached = findCached(strategyCaseId);
        if (cached.isPresent()) {
            log.info(
                    "Strategy result cache found; skipping candidate evaluation and LLM. "
                            + "strategyCaseId={}, optionCount={}",
                    strategyCaseId, cached.get().options().size()
            );
            completeFromCache(strategyCaseId, cached.get());
            return;
        }

        StrategyResultLock lock = acquire(strategyCaseId);
        log.debug(
                "Strategy result generation lock acquired. strategyCaseId={}",
                strategyCaseId
        );
        try {
            processWithLock(strategyCaseId);
        } finally {
            releaseQuietly(lock, strategyCaseId);
        }
    }

    private void processWithLock(Long strategyCaseId) {
        StrategyCaseVO latest = loadCase(strategyCaseId);
        if (isCompleteOrTerminal(latest)) return;
        requireStrategyGenerating(latest);
        Optional<StrategyGenerationResult> cached = findCached(strategyCaseId);
        if (cached.isPresent()) {
            completeFromCache(strategyCaseId, cached.get());
            return;
        }
        try {
            long evaluationStartedNanos = System.nanoTime();
            log.debug(
                    "Strategy candidate evaluation started. strategyCaseId={}",
                    strategyCaseId
            );
            StrategyCandidateEvaluationResult evaluation = evaluationService.evaluate(
                    strategyCaseId, SimulationDetailLevel.SUMMARY_ONLY
            );
            log.info(
                    "Strategy candidate evaluation completed. strategyCaseId={}, "
                            + "evaluatedCandidateCount={}, generationExclusionCount={}, "
                            + "simulationFailureCount={}, elapsedMs={}",
                    strategyCaseId, evaluation.evaluatedCandidates().size(),
                    evaluation.generationExclusions().size(),
                    evaluation.simulationFailures().size(),
                    elapsedMillis(evaluationStartedNanos)
            );
            long recommendationStartedNanos = System.nanoTime();
            log.debug(
                    "Strategy recommendation selection started. strategyCaseId={}",
                    strategyCaseId
            );
            StrategyRecommendationResult recommendation = recommendationService.recommend(
                    strategyCaseId, evaluation
            );
            log.info(
                    "Strategy recommendation selection completed. strategyCaseId={}, "
                            + "optionCount={}, noRecommendationCode={}, providerModel={}, "
                            + "elapsedMs={}",
                    strategyCaseId, recommendation.options().size(),
                    recommendation.noRecommendation() == null
                            ? null : recommendation.noRecommendation().code(),
                    recommendation.providerMetadata() == null
                            ? null : recommendation.providerMetadata().model(),
                    elapsedMillis(recommendationStartedNanos)
            );
            StrategyGenerationResult result = resultFactory.create(
                    strategyCaseId, recommendation
            );
            saveSimulationContext(recommendation.calculationContext());
            log.debug(
                    "Strategy simulation context saved. strategyCaseId={}",
                    strategyCaseId
            );
            StrategyResultCacheEntry entry = save(result);
            log.debug(
                    "Strategy result cached. strategyCaseId={}, cacheKey={}, "
                            + "expiresAt={}, optionCount={}",
                    strategyCaseId, entry.cacheKey(), entry.expiresAt(),
                    result.options().size()
            );
            complete(strategyCaseId, entry, result);
        } catch (PermanentStrategyGenerationException
                 | RetryableStrategyGenerationException exception) {
            throw exception;
        } catch (StrategyCalculationException exception) {
            throw new PermanentStrategyGenerationException(
                    exception.getCode(), STAGE, exception.getMessage(), exception
            );
        } catch (IllegalArgumentException exception) {
            throw new PermanentStrategyGenerationException(
                    "STRATEGY_CANDIDATE_UNAVAILABLE", STAGE,
                    "No valid strategy recommendation candidate is available", exception
            );
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_GENERATION_UNEXPECTED_ERROR", STAGE,
                    "Unexpected error occurred while generating AI strategy", exception
            );
        }
    }

    private void completeFromCache(Long strategyCaseId, StrategyGenerationResult result) {
        log.debug(
                "Completing strategy generation from cached result. strategyCaseId={}, "
                        + "optionCount={}",
                strategyCaseId, result.options().size()
        );
        complete(strategyCaseId, new StrategyResultCacheEntry(
                com.stockit.backend.feature.strategy.result.RedisStrategyResultStore.key(
                        strategyCaseId
                ),
                result.generatedAt().plus(resultProperties.getTtl())
        ), result);
    }

    private void complete(
            Long strategyCaseId,
            StrategyResultCacheEntry entry,
            StrategyGenerationResult result
    ) {
        try {
            if (stageService.completeStrategyGeneration(
                    strategyCaseId,
                    entry.cacheKey(),
                    entry.expiresAt(),
                    outcomeOf(result)
            )) {
                log.info(
                        "Strategy generation completed. strategyCaseId={}, "
                                + "generationStage=COMPARISON_READY, outcome={}, "
                                + "optionCount={}",
                        strategyCaseId, outcomeOf(result), result.options().size()
                );
                return;
            }
            StrategyCaseVO latest = loadCase(strategyCaseId);
            if (isCompleteOrTerminal(latest)) return;
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_STAGE_TRANSITION_FAILED", STAGE,
                    "AI strategy case could not enter COMPARISON_READY"
            );
        } catch (RetryableStrategyGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_STAGE_TRANSITION_FAILED", STAGE,
                    "Failed to persist AI strategy completion", exception
            );
        }
    }

    private static StrategyRecommendationOutcome outcomeOf(
            StrategyGenerationResult result
    ) {
        return result.options().isEmpty()
                ? StrategyRecommendationOutcome.MAINTAIN_CURRENT_STATE
                : StrategyRecommendationOutcome.OPTIONS_GENERATED;
    }

    private StrategyResultCacheEntry save(StrategyGenerationResult result) {
        try {
            return resultStore.save(result);
        } catch (InvalidStrategyResultException exception) {
            throw new PermanentStrategyGenerationException(
                    "STRATEGY_RESULT_INVALID", STAGE, exception.getMessage(), exception
            );
        } catch (StrategyResultStoreException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_RESULT_CACHE_UNAVAILABLE", STAGE,
                    exception.getMessage(), exception
            );
        }
    }

    private void saveSimulationContext(StrategyCalculationContext context) {
        try {
            simulationContextStore.save(context);
        } catch (InvalidStrategyResultException exception) {
            throw new PermanentStrategyGenerationException(
                    "STRATEGY_SIMULATION_CONTEXT_INVALID",
                    STAGE,
                    exception.getMessage(),
                    exception
            );
        } catch (StrategyResultStoreException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_RESULT_CACHE_UNAVAILABLE",
                    STAGE,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Optional<StrategyGenerationResult> findCached(Long strategyCaseId) {
        try {
            return resultStore.find(strategyCaseId);
        } catch (InvalidStrategyResultException exception) {
            throw new PermanentStrategyGenerationException(
                    "STRATEGY_RESULT_INVALID", STAGE, exception.getMessage(), exception
            );
        } catch (StrategyResultStoreException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_RESULT_CACHE_UNAVAILABLE", STAGE,
                    exception.getMessage(), exception
            );
        }
    }

    private StrategyResultLock acquire(Long strategyCaseId) {
        try {
            return lockManager.tryAcquire(strategyCaseId).orElseThrow(() ->
                    new StrategyGenerationBusyException(
                            "AI strategy result is already being generated: " + strategyCaseId
                    ));
        } catch (StrategyGenerationBusyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "STRATEGY_RESULT_CACHE_UNAVAILABLE", STAGE,
                    "Failed to acquire AI strategy generation lock", exception
            );
        }
    }

    private void releaseQuietly(StrategyResultLock lock, Long strategyCaseId) {
        try {
            lockManager.release(lock);
        } catch (RuntimeException exception) {
            log.warn("AI strategy result lock release failed; waiting for TTL. caseId={}",
                    strategyCaseId, exception);
        }
    }

    private StrategyCaseVO loadCase(Long strategyCaseId) {
        StrategyCaseVO value = caseMapper.selectStrategyCaseById(strategyCaseId);
        if (value == null) {
            throw new PermanentStrategyGenerationException(
                    "MQ_CASE_NOT_FOUND", STAGE,
                    "AI strategy case does not exist: " + strategyCaseId
            );
        }
        return value;
    }

    private static void requireStrategyGenerating(StrategyCaseVO value) {
        if (value.getCaseStatus() != StrategyCaseStatus.GENERATING
                || value.getGenerationStage() != STAGE) {
            throw new PermanentStrategyGenerationException(
                    "STRATEGY_CASE_STAGE_INVALID", STAGE,
                    "AI strategy case is not ready for recommendation"
            );
        }
    }

    private static boolean isCompleteOrTerminal(StrategyCaseVO value) {
        return value.getCaseStatus() != StrategyCaseStatus.GENERATING
                || value.getGenerationStage() == StrategyGenerationStage.COMPARISON_READY;
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
