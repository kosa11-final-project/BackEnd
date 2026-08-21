package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.stockit.backend.feature.strategy.service.StrategyGenerationFailureService;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobHandler;
import com.stockit.backend.feature.strategy.service.StrategyGenerationRetryPublisher;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StrategyGenerationJobListenerTest {

    private static final long DELIVERY_TAG = 77L;

    @Mock
    private StrategyGenerationJobHandler jobHandler;

    @Mock
    private StrategyGenerationRetryPublisher retryPublisher;

    @Mock
    private StrategyGenerationFailureService failureService;

    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private StrategyGenerationJobListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        StrategyGenerationMessagingProperties properties =
                new StrategyGenerationMessagingProperties();
        properties.setMaxAttempts(3);
        listener = new StrategyGenerationJobListener(
                objectMapper,
                jobHandler,
                retryPublisher,
                failureService,
                properties
        );
    }

    @Test
    void acknowledgesOnlyAfterSuccessfulHandling() throws Exception {
        StrategyGenerationJobMessage message = message();

        listener.consume(rawMessage(message, 0), channel);

        verify(jobHandler).handle(message);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(retryPublisher, never()).publishForRetry(any(), any(Integer.class));
    }

    @Test
    void publishesTransientFailureToRetryBeforeAcknowledgingOriginal() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new IllegalStateException("temporary database failure"))
                .when(jobHandler).handle(message);

        listener.consume(rawMessage(message, 0), channel);

        verify(retryPublisher).publishForRetry(message, 1);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void recordsFailureAndRejectsAfterThirdAttempt() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new IllegalStateException("temporary database failure"))
                .when(jobHandler).handle(message);

        listener.consume(rawMessage(message, 2), channel);

        verify(failureService).markFailed(
                12345L,
                null,
                "MQ_RETRY_EXHAUSTED",
                "[MQ_RETRY_EXHAUSTED] temporary database failure"
        );
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(retryPublisher, never()).publishForRetry(any(), any(Integer.class));
    }

    @Test
    void recordsForecastingStageWhenTypedRetriesAreExhausted() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new RetryableStrategyGenerationException(
                "FORECAST_UNEXPECTED_ERROR",
                StrategyGenerationStage.FORECASTING,
                "Unexpected error occurred while processing demand forecast"
        )).when(jobHandler).handle(message);

        listener.consume(rawMessage(message, 2), channel);

        verify(failureService).markFailed(
                12345L,
                StrategyGenerationStage.FORECASTING,
                "MQ_RETRY_EXHAUSTED",
                "[FORECAST_UNEXPECTED_ERROR] Unexpected error occurred while processing "
                        + "demand forecast"
        );
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(retryPublisher, never()).publishForRetry(any(), any(Integer.class));
    }

    @Test
    void logsWhenConditionalFailureRecordDoesNotMatchCurrentCase(
            CapturedOutput output
    ) throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new PermanentStrategyGenerationException(
                "FORECAST_UNAVAILABLE",
                StrategyGenerationStage.FORECASTING,
                "forecast unavailable"
        )).when(jobHandler).handle(message);
        org.mockito.Mockito.when(failureService.markFailed(
                12345L,
                StrategyGenerationStage.FORECASTING,
                "FORECAST_UNAVAILABLE",
                "forecast unavailable"
        )).thenReturn(false);

        listener.consume(rawMessage(message, 0), channel);

        assertThat(output)
                .contains("AI strategy failure was not persisted because case state changed")
                .contains("strategyCaseId=12345")
                .contains("expectedStage=FORECASTING")
                .contains("failureCode=FORECAST_UNAVAILABLE");
        verify(channel).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void sendsPermanentFailureDirectlyToDlqWithoutRetry() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new PermanentStrategyGenerationException(
                "MQ_PAYLOAD_INVALID",
                StrategyGenerationStage.FORECASTING,
                "stored payload is invalid"
        )).when(jobHandler).handle(message);

        listener.consume(rawMessage(message, 0), channel);

        verify(failureService).markFailed(
                12345L,
                StrategyGenerationStage.FORECASTING,
                "MQ_PAYLOAD_INVALID",
                "stored payload is invalid"
        );
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(retryPublisher, never()).publishForRetry(any(), any(Integer.class));
    }

    @Test
    void delaysBusyCaseWithoutIncreasingApiRetryCount() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new StrategyGenerationBusyException("owned by another worker"))
                .when(jobHandler).handle(message);

        listener.consume(rawMessage(message, 2), channel);

        verify(retryPublisher).publishForRetry(message, 2);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(failureService, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void requeuesOriginalWhenRetryPublicationCannotBeConfirmed() throws Exception {
        StrategyGenerationJobMessage message = message();
        doThrow(new IllegalStateException("temporary failure"))
                .when(jobHandler).handle(message);
        doThrow(new StrategyGenerationPublishException("confirm timeout"))
                .when(retryPublisher).publishForRetry(message, 1);

        listener.consume(rawMessage(message, 0), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void rejectsMalformedJsonWithoutTryingToPersistUnknownCase() throws Exception {
        Message malformed = MessageBuilder
                .withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .setDeliveryTag(DELIVERY_TAG)
                .build();

        listener.consume(malformed, channel);

        verify(failureService, never()).markFailed(any(), any(), any(), any());
        verify(channel).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void rejectsJsonNullAsPermanentFailureWithoutRetry() throws Exception {
        Message jsonNull = MessageBuilder
                .withBody("null".getBytes(StandardCharsets.UTF_8))
                .setDeliveryTag(DELIVERY_TAG)
                .build();

        listener.consume(jsonNull, channel);

        verify(jobHandler, never()).handle(any());
        verify(retryPublisher, never()).publishForRetry(any(), any(Integer.class));
        verify(failureService, never()).markFailed(any(), any(), any(), any());
        verify(channel).basicReject(DELIVERY_TAG, false);
    }

    private Message rawMessage(
            StrategyGenerationJobMessage payload,
            int retryCount
    ) throws Exception {
        return MessageBuilder
                .withBody(objectMapper.writeValueAsBytes(payload))
                .setDeliveryTag(DELIVERY_TAG)
                .setHeader(
                        StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER,
                        retryCount
                )
                .build();
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
