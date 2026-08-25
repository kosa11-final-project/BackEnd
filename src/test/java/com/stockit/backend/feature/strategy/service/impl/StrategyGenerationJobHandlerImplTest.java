package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.forecast.DailyForecastPrediction;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpoint;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpointStore;
import com.stockit.backend.feature.strategy.forecast.ForecastLock;
import com.stockit.backend.feature.strategy.forecast.ForecastLockManager;
import com.stockit.backend.feature.strategy.forecast.ForecastModelVersionResolver;
import com.stockit.backend.feature.strategy.forecast.ForecastProvider;
import com.stockit.backend.feature.strategy.forecast.SalesPointForecast;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequest;
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
import com.stockit.backend.feature.strategy.service.StrategyGenerationStageService;
import com.stockit.backend.feature.strategy.service.StrategyRecommendationStageProcessor;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationJobHandlerImplTest {

    @Mock
    private StrategyCaseMapper strategyCaseMapper;
    @Mock
    private StrategyCaseRequestPayloadSerializer payloadSerializer;
    @Mock
    private StrategyForecastRequestFactory requestFactory;
    @Mock
    private ForecastCheckpointStore checkpointStore;
    @Mock
    private ForecastLockManager lockManager;
    @Mock
    private ForecastProvider forecastProvider;
    @Mock
    private StrategyForecastResponseValidator responseValidator;
    @Mock
    private ForecastModelVersionResolver modelVersionResolver;
    @Mock
    private StrategyGenerationStageService stageService;
    @Mock
    private StrategyRecommendationStageProcessor recommendationStageProcessor;

    private StrategyGenerationJobHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new StrategyGenerationJobHandlerImpl(
                strategyCaseMapper,
                payloadSerializer,
                requestFactory,
                checkpointStore,
                lockManager,
                forecastProvider,
                responseValidator,
                modelVersionResolver,
                stageService,
                recommendationStageProcessor
        );
    }

    @Test
    void callsForecastStoresCheckpointAndAdvancesPendingCase() {
        StrategyCaseVO pending = generatingCase(null);
        givenContextFor(pending);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(pending, pending);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(stageService.enterForecasting(12345L)).thenReturn(true);
        when(forecastProvider.forecast(context().request())).thenReturn(response());
        when(modelVersionResolver.resolve(response())).thenReturn(81L);
        when(stageService.completeForecasting(12345L)).thenReturn(true);

        handler.handle(message());

        verify(forecastProvider).forecast(context().request());
        verify(checkpointStore).save(argThat(checkpoint ->
                checkpoint.modelVersionId().equals(81L)
                        && checkpoint.forecastResponse().equals(response())
        ));
        verify(stageService).completeForecasting(12345L);
        verify(lockManager).release(lock());
    }

    @Test
    void resumesForecastingCaseWithoutTryingToEnterStageAgain() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request())).thenReturn(response());
        when(modelVersionResolver.resolve(response())).thenReturn(81L);
        when(stageService.completeForecasting(12345L)).thenReturn(true);

        handler.handle(message());

        verify(stageService, never()).enterForecasting(12345L);
        verify(forecastProvider).forecast(context().request());
    }

    @Test
    void usesCheckpointWithoutCallingForecastApi() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        ForecastCheckpoint checkpoint = ForecastCheckpoint.create(
                context(),
                response(),
                81L,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.of(checkpoint));
        when(stageService.completeForecasting(12345L)).thenReturn(true);

        handler.handle(message());

        verify(responseValidator).validate(context(), response());
        verify(forecastProvider, never()).forecast(any());
        verify(lockManager, never()).tryAcquire(any());
    }

    @Test
    void recoversDbTransitionFailureFromSavedCheckpointWithoutSecondApiCall() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        ForecastCheckpoint checkpoint = ForecastCheckpoint.create(
                context(),
                response(),
                81L,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting, forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(checkpoint)
                );
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request())).thenReturn(response());
        when(modelVersionResolver.resolve(response())).thenReturn(81L);
        when(stageService.completeForecasting(12345L)).thenReturn(false, true);

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOf(RetryableStrategyGenerationException.class);
        assertThatCode(() -> handler.handle(message())).doesNotThrowAnyException();

        verify(forecastProvider).forecast(context().request());
        verify(checkpointStore).save(any(ForecastCheckpoint.class));
        verify(lockManager).release(lock());
    }

    @Test
    void releasesLockAndSkipsCheckpointWhenForecastApiFails() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request()))
                .thenThrow(new RetryableStrategyGenerationException(
                        "FORECAST_API_UNAVAILABLE",
                        StrategyGenerationStage.FORECASTING,
                        "forecast api unavailable"
                ));

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOf(RetryableStrategyGenerationException.class);

        verify(lockManager).release(lock());
        verify(checkpointStore, never()).save(any());
        verify(stageService, never()).completeForecasting(12345L);
    }

    @Test
    void wrapsUnexpectedForecastingFailureWithCurrentStageAndReleasesLock() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        IllegalStateException cause = new IllegalStateException("unexpected client failure");
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request())).thenThrow(cause);

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("FORECAST_UNEXPECTED_ERROR");
                            assertThat(exception.getExpectedStage())
                                    .isEqualTo(StrategyGenerationStage.FORECASTING);
                            assertThat(exception.getCause()).isSameAs(cause);
                        }
                );

        verify(lockManager).release(lock());
        verify(checkpointStore, never()).save(any());
        verify(stageService, never()).completeForecasting(12345L);
    }

    @Test
    void releasesLockAndSkipsCheckpointWhenResponseValidationFails() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request())).thenReturn(response());
        doThrow(new PermanentStrategyGenerationException(
                "FORECAST_RESPONSE_INVALID",
                StrategyGenerationStage.FORECASTING,
                "invalid forecast response"
        )).when(responseValidator).validate(context(), response());

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOf(PermanentStrategyGenerationException.class);

        verify(lockManager).release(lock());
        verify(checkpointStore, never()).save(any());
        verify(stageService, never()).completeForecasting(12345L);
    }

    @Test
    void preservesRetryableForecastingFailureWithoutReplacingItsCode() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        RetryableStrategyGenerationException cause =
                new RetryableStrategyGenerationException(
                        "FORECAST_API_UNAVAILABLE",
                        StrategyGenerationStage.FORECASTING,
                        "forecast api unavailable"
                );
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting, forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.of(lock()));
        when(forecastProvider.forecast(context().request())).thenThrow(cause);

        assertThatThrownBy(() -> handler.handle(message())).isSameAs(cause);

        verify(lockManager).release(lock());
    }

    @Test
    void wrapsUnexpectedCheckpointRecoveryFailureAtForecastingStage() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        ForecastCheckpoint checkpoint = ForecastCheckpoint.create(
                context(),
                response(),
                81L,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        IllegalStateException cause = new IllegalStateException("unexpected validation failure");
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.of(checkpoint));
        doThrow(cause).when(responseValidator).validate(context(), response());

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("FORECAST_UNEXPECTED_ERROR");
                            assertThat(exception.getExpectedStage())
                                    .isEqualTo(StrategyGenerationStage.FORECASTING);
                            assertThat(exception.getCause()).isSameAs(cause);
                        }
                );

        verify(forecastProvider, never()).forecast(any());
        verify(lockManager, never()).tryAcquire(any());
    }

    @Test
    void wrapsUnexpectedCheckpointAccessFailureAtForecastingStage() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        IllegalStateException cause = new IllegalStateException("unexpected redis failure");
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenThrow(cause);

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("FORECAST_UNEXPECTED_ERROR");
                            assertThat(exception.getExpectedStage())
                                    .isEqualTo(StrategyGenerationStage.FORECASTING);
                            assertThat(exception.getCause()).isSameAs(cause);
                        }
                );

        verify(lockManager, never()).tryAcquire(any());
        verify(forecastProvider, never()).forecast(any());
    }

    @Test
    void doesNotLabelUnexpectedPendingCheckpointFailureAsForecasting() {
        StrategyCaseVO pending = generatingCase(null);
        ForecastCheckpoint checkpoint = ForecastCheckpoint.create(
                context(),
                response(),
                81L,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        IllegalStateException cause = new IllegalStateException("unexpected validation failure");
        givenContextFor(pending);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(pending);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.of(checkpoint));
        doThrow(cause).when(responseValidator).validate(context(), response());

        assertThatThrownBy(() -> handler.handle(message())).isSameAs(cause);

        verify(stageService, never()).enterForecasting(any());
        verify(stageService, never()).completeForecasting(any());
    }

    @Test
    void reportsLockContentionWithoutCallingForecastApi() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        givenContextFor(forecasting);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(forecasting);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L)))
                .thenReturn(Optional.empty());
        when(lockManager.tryAcquire(12345L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOf(StrategyGenerationBusyException.class);

        verify(forecastProvider, never()).forecast(any());
    }

    @Test
    void delegatesStrategyGeneratingCaseToRecommendationProcessor() {
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(generatingCase(StrategyGenerationStage.STRATEGY_GENERATING));

        assertThatCode(() -> handler.handle(message())).doesNotThrowAnyException();

        verify(payloadSerializer, never()).deserialize(any());
        verify(checkpointStore, never()).find(any(), any(), any());
        verify(recommendationStageProcessor).process(12345L);
    }

    @Test
    void rejectsUnknownCaseAsPermanentFailure() {
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(null);

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("MQ_CASE_NOT_FOUND")
                );
    }

    @Test
    void rejectsCorruptedStoredPayloadAtCurrentStage() {
        StrategyCaseVO forecasting = generatingCase(StrategyGenerationStage.FORECASTING);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(forecasting);
        when(payloadSerializer.deserialize(any()))
                .thenThrow(new StrategyCasePayloadException("invalid payload", null));

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("MQ_PAYLOAD_INVALID");
                            assertThat(exception.getExpectedStage())
                                    .isEqualTo(StrategyGenerationStage.FORECASTING);
                        }
                );
    }

    @Test
    void rejectsUnsupportedMessageSchema() {
        StrategyGenerationJobMessage unsupported = new StrategyGenerationJobMessage(
                2,
                message().messageId(),
                12345L,
                message().requestedAt()
        );

        assertThatThrownBy(() -> handler.handle(unsupported))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("MQ_MESSAGE_INVALID")
                );
        verify(strategyCaseMapper, never()).selectStrategyCaseById(12345L);
    }

    private void givenContextFor(StrategyCaseVO strategyCase) {
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                List.of(),
                List.of(10L),
                List.of(),
                null,
                null,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20)
        );
        when(payloadSerializer.deserialize(strategyCase.getRequestPayloadJson()))
                .thenReturn(payload);
        when(requestFactory.create(strategyCase, payload)).thenReturn(context());
    }

    private static StrategyForecastRequestContext context() {
        return new StrategyForecastRequestContext(
                new StrategyForecastRequest(
                        12345L,
                        1001L,
                        10L,
                        List.of(10L),
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 20)
                ),
                List.of(10L),
                "request-hash"
        );
    }

    private static StrategyForecastResponse response() {
        return new StrategyForecastResponse(
                12345L,
                1001L,
                10L,
                List.of(10L),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20),
                1,
                "forecast-run-1",
                "stockit-demand-lightgbm",
                "3",
                OffsetDateTime.parse("2026-08-20T10:15:30+09:00"),
                List.of(new SalesPointForecast(
                        10L,
                        true,
                        List.of(new DailyForecastPrediction(
                                LocalDate.of(2026, 8, 20),
                                new BigDecimal("14.1")
                        ))
                ))
        );
    }

    private static ForecastLock lock() {
        return new ForecastLock(
                "ai-strategy:case:12345:lock:forecast",
                "owner-token"
        );
    }

    private static StrategyCaseVO generatingCase(StrategyGenerationStage stage) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setStrategyCaseId(12345L);
        strategyCase.setSkuId(1001L);
        strategyCase.setRequestedSalesPointId(10L);
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setGenerationStage(stage);
        strategyCase.setRequestPayloadJson("{\"forecastStartDate\":\"2026-08-20\"}");
        return strategyCase;
    }

    private static StrategyGenerationJobMessage message() {
        return new StrategyGenerationJobMessage(
                1,
                UUID.fromString("3384b213-5c0e-4b87-a0f0-15cf0f7f650d"),
                12345L,
                OffsetDateTime.of(2026, 8, 20, 14, 30, 0, 0, ZoneOffset.ofHours(9))
        );
    }
}
