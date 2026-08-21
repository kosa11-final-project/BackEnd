package com.stockit.backend.feature.strategy.service.impl;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpoint;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpointAccessException;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpointStore;
import com.stockit.backend.feature.strategy.forecast.ForecastLock;
import com.stockit.backend.feature.strategy.forecast.ForecastLockAccessException;
import com.stockit.backend.feature.strategy.forecast.ForecastLockManager;
import com.stockit.backend.feature.strategy.forecast.ForecastProvider;
import com.stockit.backend.feature.strategy.forecast.InvalidForecastCheckpointException;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestContext;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestFactory;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponse;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponseValidator;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationBusyException;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;
import com.stockit.backend.feature.strategy.service.StrategyCasePayloadException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobHandler;
import com.stockit.backend.feature.strategy.service.StrategyGenerationStageService;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/**
 * 실제 수요예측을 Redis 체크포인트로 보존하고 중단된 FORECASTING을 재개
 */
@Service
public class StrategyGenerationJobHandlerImpl implements StrategyGenerationJobHandler {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyGenerationJobHandlerImpl.class
    );
    private static final String INVALID_MESSAGE_CODE = "MQ_MESSAGE_INVALID";
    private static final String CASE_NOT_FOUND_CODE = "MQ_CASE_NOT_FOUND";
    private static final String PAYLOAD_INVALID_CODE = "MQ_PAYLOAD_INVALID";

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final StrategyForecastRequestFactory requestFactory;
    private final ForecastCheckpointStore checkpointStore;
    private final ForecastLockManager lockManager;
    private final ForecastProvider forecastProvider;
    private final StrategyForecastResponseValidator responseValidator;
    private final StrategyGenerationStageService stageService;

    public StrategyGenerationJobHandlerImpl(
            StrategyCaseMapper strategyCaseMapper,
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            StrategyForecastRequestFactory requestFactory,
            ForecastCheckpointStore checkpointStore,
            ForecastLockManager lockManager,
            ForecastProvider forecastProvider,
            StrategyForecastResponseValidator responseValidator,
            StrategyGenerationStageService stageService
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.payloadSerializer = payloadSerializer;
        this.requestFactory = requestFactory;
        this.checkpointStore = checkpointStore;
        this.lockManager = lockManager;
        this.forecastProvider = forecastProvider;
        this.responseValidator = responseValidator;
        this.stageService = stageService;
    }

    @Override
    public void handle(StrategyGenerationJobMessage message) {
        validateMessage(message);
        StrategyCaseVO strategyCase = loadCase(message.strategyCaseId());
        if (isForecastStepCompleteOrTerminal(strategyCase)) {
            return;
        }

        StrategyForecastRequestContext context = createContext(strategyCase);
        Optional<ForecastCheckpoint> checkpoint = findCheckpoint(
                context,
                strategyCase.getGenerationStage()
        );
        if (checkpoint.isPresent()) {
            completeFromCheckpoint(strategyCase, context, checkpoint.get());
            return;
        }

        ForecastLock lock = acquireLock(
                message.strategyCaseId(),
                strategyCase.getGenerationStage()
        );
        try {
            processWhileOwningLock(message.strategyCaseId());
        } finally {
            releaseQuietly(lock, message.strategyCaseId());
        }
    }

    private void processWhileOwningLock(Long strategyCaseId) {
        StrategyCaseVO latest = loadCase(strategyCaseId);
        if (isForecastStepCompleteOrTerminal(latest)) {
            return;
        }

        StrategyForecastRequestContext context = createContext(latest);
        Optional<ForecastCheckpoint> checkpoint = findCheckpoint(
                context,
                latest.getGenerationStage()
        );
        if (checkpoint.isPresent()) {
            completeFromCheckpoint(latest, context, checkpoint.get());
            return;
        }

        if (!ensureForecasting(latest)) {
            return;
        }
        StrategyForecastResponse response = forecastProvider.forecast(context.request());
        responseValidator.validate(context, response);
        saveCheckpoint(ForecastCheckpoint.create(context, response, Instant.now()));
        completeForecasting(strategyCaseId);
    }

    private StrategyCaseVO loadCase(Long strategyCaseId) {
        StrategyCaseVO strategyCase = strategyCaseMapper.selectStrategyCaseById(
                strategyCaseId
        );
        if (strategyCase == null) {
            throw new PermanentStrategyGenerationException(
                    CASE_NOT_FOUND_CODE,
                    "AI strategy case does not exist: " + strategyCaseId
            );
        }
        return strategyCase;
    }

    private StrategyForecastRequestContext createContext(StrategyCaseVO strategyCase) {
        try {
            StrategyCaseRequestPayload payload = payloadSerializer.deserialize(
                    strategyCase.getRequestPayloadJson()
            );
            return requestFactory.create(strategyCase, payload);
        } catch (PermanentStrategyGenerationException exception) {
            throw exception;
        } catch (StrategyCasePayloadException exception) {
            throw new PermanentStrategyGenerationException(
                    PAYLOAD_INVALID_CODE,
                    strategyCase.getGenerationStage(),
                    exception.getMessage(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_REFERENCE_LOOKUP_FAILED",
                    strategyCase.getGenerationStage(),
                    "Failed to resolve demand forecast request references",
                    exception
            );
        }
    }

    private Optional<ForecastCheckpoint> findCheckpoint(
            StrategyForecastRequestContext context,
            StrategyGenerationStage expectedStage
    ) {
        try {
            return checkpointStore.find(
                    context.request().strategyRequestId(),
                    context.requestHash(),
                    context.expectedSalesPointIds()
            );
        } catch (InvalidForecastCheckpointException exception) {
            throw new PermanentStrategyGenerationException(
                    "FORECAST_CHECKPOINT_INVALID",
                    expectedStage,
                    exception.getMessage(),
                    exception
            );
        } catch (ForecastCheckpointAccessException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_CACHE_UNAVAILABLE",
                    expectedStage,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void saveCheckpoint(ForecastCheckpoint checkpoint) {
        try {
            checkpointStore.save(checkpoint);
        } catch (InvalidForecastCheckpointException exception) {
            throw new PermanentStrategyGenerationException(
                    "FORECAST_CHECKPOINT_INVALID",
                    StrategyGenerationStage.FORECASTING,
                    exception.getMessage(),
                    exception
            );
        } catch (ForecastCheckpointAccessException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_CACHE_UNAVAILABLE",
                    StrategyGenerationStage.FORECASTING,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private ForecastLock acquireLock(
            Long strategyCaseId,
            StrategyGenerationStage expectedStage
    ) {
        try {
            return lockManager.tryAcquire(strategyCaseId).orElseThrow(() ->
                    new StrategyGenerationBusyException(
                            "Demand forecast is already being processed: "
                                    + strategyCaseId
                    ));
        } catch (ForecastLockAccessException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_CACHE_UNAVAILABLE",
                    expectedStage,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private boolean ensureForecasting(StrategyCaseVO strategyCase) {
        if (strategyCase.getGenerationStage() == StrategyGenerationStage.FORECASTING) {
            return true;
        }
        if (strategyCase.getGenerationStage() != null) {
            return false;
        }
        if (enterForecasting(strategyCase.getStrategyCaseId())) {
            return true;
        }

        StrategyCaseVO latest = loadCase(strategyCase.getStrategyCaseId());
        if (latest.getCaseStatus() != StrategyCaseStatus.GENERATING
                || latest.getGenerationStage() == StrategyGenerationStage.FORECASTING
                || isAfterForecasting(latest.getGenerationStage())) {
            return latest.getCaseStatus() == StrategyCaseStatus.GENERATING
                    && latest.getGenerationStage() == StrategyGenerationStage.FORECASTING;
        }
        throw new RetryableStrategyGenerationException(
                "FORECAST_STAGE_TRANSITION_FAILED",
                null,
                "AI strategy case could not enter FORECASTING: "
                        + strategyCase.getStrategyCaseId()
        );
    }

    private void completeFromCheckpoint(
            StrategyCaseVO strategyCase,
            StrategyForecastRequestContext context,
            ForecastCheckpoint checkpoint
    ) {
        responseValidator.validate(context, checkpoint.forecastResponse());
        if (strategyCase.getGenerationStage() == null
                && !enterForecasting(strategyCase.getStrategyCaseId())) {
            StrategyCaseVO latest = loadCase(strategyCase.getStrategyCaseId());
            if (isForecastStepCompleteOrTerminal(latest)) {
                return;
            }
            if (latest.getGenerationStage() != StrategyGenerationStage.FORECASTING) {
                throw new RetryableStrategyGenerationException(
                        "FORECAST_STAGE_TRANSITION_FAILED",
                        null,
                        "AI strategy case could not recover FORECASTING"
                );
            }
        }
        completeForecasting(strategyCase.getStrategyCaseId());
    }

    private void completeForecasting(Long strategyCaseId) {
        boolean completed;
        try {
            completed = stageService.completeForecasting(strategyCaseId);
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_STAGE_TRANSITION_FAILED",
                    StrategyGenerationStage.FORECASTING,
                    "Failed to persist STRATEGY_GENERATING transition",
                    exception
            );
        }
        if (completed) {
            return;
        }
        StrategyCaseVO latest = loadCase(strategyCaseId);
        if (isForecastStepCompleteOrTerminal(latest)) {
            return;
        }
        throw new RetryableStrategyGenerationException(
                "FORECAST_STAGE_TRANSITION_FAILED",
                StrategyGenerationStage.FORECASTING,
                "AI strategy case could not enter STRATEGY_GENERATING: "
                        + strategyCaseId
        );
    }

    private boolean enterForecasting(Long strategyCaseId) {
        try {
            return stageService.enterForecasting(strategyCaseId);
        } catch (RuntimeException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_STAGE_TRANSITION_FAILED",
                    null,
                    "Failed to persist FORECASTING transition",
                    exception
            );
        }
    }

    private void releaseQuietly(ForecastLock lock, Long strategyCaseId) {
        try {
            lockManager.release(lock);
        } catch (RuntimeException exception) {
            log.warn(
                    "Demand forecast lock release failed; waiting for TTL. strategyCaseId={}",
                    strategyCaseId,
                    exception
            );
        }
    }

    private static boolean isForecastStepCompleteOrTerminal(StrategyCaseVO strategyCase) {
        return strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATING
                || isAfterForecasting(strategyCase.getGenerationStage());
    }

    private static boolean isAfterForecasting(StrategyGenerationStage stage) {
        return stage == StrategyGenerationStage.STRATEGY_GENERATING
                || stage == StrategyGenerationStage.COMPARISON_READY;
    }

    private static void validateMessage(StrategyGenerationJobMessage message) {
        if (message == null
                || message.schemaVersion()
                != StrategyGenerationJobMessage.CURRENT_SCHEMA_VERSION
                || message.messageId() == null
                || message.strategyCaseId() == null
                || message.strategyCaseId() <= 0
                || message.requestedAt() == null) {
            throw new PermanentStrategyGenerationException(
                    INVALID_MESSAGE_CODE,
                    "AI strategy generation message contract is invalid"
            );
        }
    }
}
