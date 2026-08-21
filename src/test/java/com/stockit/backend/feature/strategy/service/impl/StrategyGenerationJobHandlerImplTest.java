package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;
import com.stockit.backend.feature.strategy.service.StrategyCasePayloadException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationJobHandlerImplTest {

    @Mock
    private StrategyCaseMapper strategyCaseMapper;

    @Mock
    private StrategyCaseRequestPayloadSerializer payloadSerializer;

    private StrategyGenerationJobHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new StrategyGenerationJobHandlerImpl(
                strategyCaseMapper,
                payloadSerializer
        );
    }

    @Test
    void validatesStoredPayloadAndMovesPendingCaseToForecasting() {
        StrategyCaseVO strategyCase = generatingCase(null);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(strategyCase);
        when(strategyCaseMapper.markForecastingIfPending(12345L)).thenReturn(1);

        handler.handle(message());

        verify(payloadSerializer).deserialize("{\"forecastStartDate\":\"2026-08-20\"}");
        verify(strategyCaseMapper).markForecastingIfPending(12345L);
    }

    @Test
    void treatsAlreadyStartedCaseAsIdempotentNoOp() {
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(generatingCase(StrategyGenerationStage.FORECASTING));

        assertThatCode(() -> handler.handle(message())).doesNotThrowAnyException();

        verify(payloadSerializer, never()).deserialize(org.mockito.ArgumentMatchers.any());
        verify(strategyCaseMapper, never()).markForecastingIfPending(12345L);
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
    void rejectsCorruptedStoredPayloadAsPermanentFailure() {
        when(strategyCaseMapper.selectStrategyCaseById(12345L))
                .thenReturn(generatingCase(null));
        when(payloadSerializer.deserialize(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StrategyCasePayloadException("invalid payload", null));

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("MQ_PAYLOAD_INVALID")
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

    private static StrategyCaseVO generatingCase(StrategyGenerationStage stage) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setStrategyCaseId(12345L);
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setGenerationStage(stage);
        strategyCase.setRequestPayloadJson(
                "{\"forecastStartDate\":\"2026-08-20\"}"
        );
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
