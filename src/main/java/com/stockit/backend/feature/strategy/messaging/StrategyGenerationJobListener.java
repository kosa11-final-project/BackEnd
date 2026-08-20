package com.stockit.backend.feature.strategy.messaging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.rabbitmq.client.Channel;
import com.stockit.backend.feature.strategy.service.StrategyGenerationFailureService;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobHandler;
import com.stockit.backend.feature.strategy.service.StrategyGenerationRetryPublisher;

/**
 * AI 전략 생성 메시지의 ACK, 지연 재시도와 DLQ 분기를 담당하는 RabbitMQ Adapter
 */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StrategyGenerationJobListener {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyGenerationJobListener.class
    );
    private static final String INVALID_MESSAGE_CODE = "MQ_MESSAGE_INVALID";
    private static final String RETRY_EXHAUSTED_CODE = "MQ_RETRY_EXHAUSTED";

    private final ObjectMapper objectMapper;
    private final StrategyGenerationJobHandler jobHandler;
    private final StrategyGenerationRetryPublisher retryPublisher;
    private final StrategyGenerationFailureService failureService;
    private final StrategyGenerationMessagingProperties properties;

    public StrategyGenerationJobListener(
            ObjectMapper objectMapper,
            StrategyGenerationJobHandler jobHandler,
            StrategyGenerationRetryPublisher retryPublisher,
            StrategyGenerationFailureService failureService,
            StrategyGenerationMessagingProperties properties
    ) {
        this.objectMapper = objectMapper.copy().disable(
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        );
        this.jobHandler = jobHandler;
        this.retryPublisher = retryPublisher;
        this.failureService = failureService;
        this.properties = properties;
    }

    @RabbitListener(queues = StrategyGenerationMessagingProperties.MAIN_QUEUE)
    public void consume(Message rawMessage, Channel channel) throws IOException {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        StrategyGenerationJobMessage jobMessage = null;

        try {
            int retryCount = readRetryCount(rawMessage);
            jobMessage = deserialize(rawMessage);
            putLogContext(jobMessage);

            jobHandler.handle(jobMessage);
            channel.basicAck(deliveryTag, false);
            log.info(
                    "AI strategy generation message handled. retryCount={}",
                    retryCount
            );
        } catch (PermanentStrategyGenerationException exception) {
            recordFailure(jobMessage, exception.getFailureCode(), exception.getMessage());
            log.error("Permanent AI strategy message failure; routing to DLQ", exception);
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            handleTransientFailure(rawMessage, jobMessage, channel, deliveryTag, exception);
        } finally {
            MDC.remove("messageId");
            MDC.remove("strategyCaseId");
        }
    }

    private StrategyGenerationJobMessage deserialize(Message rawMessage) {
        try {
            return objectMapper.readValue(
                    rawMessage.getBody(),
                    StrategyGenerationJobMessage.class
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new PermanentStrategyGenerationException(
                    INVALID_MESSAGE_CODE,
                    "AI strategy generation message JSON is invalid",
                    exception
            );
        }
    }

    private static int readRetryCount(Message rawMessage) {
        Object value = rawMessage.getMessageProperties().getHeaders().get(
                StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER
        );
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number && number.intValue() >= 0) {
            return number.intValue();
        }
        throw new PermanentStrategyGenerationException(
                INVALID_MESSAGE_CODE,
                "AI strategy retry count header is invalid"
        );
    }

    private void handleTransientFailure(
            Message rawMessage,
            StrategyGenerationJobMessage jobMessage,
            Channel channel,
            long deliveryTag,
            RuntimeException exception
    ) throws IOException {
        int retryCount = readRetryCount(rawMessage);
        int nextRetryCount = retryCount + 1;
        if (nextRetryCount < properties.getMaxAttempts()) {
            try {
                retryPublisher.publishForRetry(jobMessage, nextRetryCount);
                channel.basicAck(deliveryTag, false);
                log.warn(
                        "AI strategy generation will be retried. retryCount={}",
                        nextRetryCount,
                        exception
                );
                return;
            } catch (RuntimeException retryPublishException) {
                log.error(
                        "Failed to publish AI strategy retry message; requeueing original",
                        retryPublishException
                );
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }

        recordFailure(jobMessage, RETRY_EXHAUSTED_CODE, exception.getMessage());
        log.error(
                "AI strategy generation retries exhausted; routing to DLQ. attempts={}",
                properties.getMaxAttempts(),
                exception
        );
        channel.basicReject(deliveryTag, false);
    }

    private void recordFailure(
            StrategyGenerationJobMessage message,
            String failureCode,
            String failureMessage
    ) {
        if (message == null || message.strategyCaseId() == null) {
            return;
        }
        try {
            failureService.markFailed(
                    message.strategyCaseId(),
                    failureCode,
                    failureMessage
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to persist AI strategy generation failure. strategyCaseId={}",
                    message.strategyCaseId(),
                    exception
            );
        }
    }

    private static void putLogContext(StrategyGenerationJobMessage message) {
        if (message.messageId() != null) {
            MDC.put("messageId", message.messageId().toString());
        }
        if (message.strategyCaseId() != null) {
            MDC.put("strategyCaseId", message.strategyCaseId().toString());
        }
    }
}
