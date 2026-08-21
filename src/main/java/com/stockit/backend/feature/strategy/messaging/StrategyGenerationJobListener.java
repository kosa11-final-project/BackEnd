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
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

/**
 * AI 전략 생성 메시지의 ACK, 지연 재시도와 DLQ 분기를 담당하는 RabbitMQ Adapter
 *
 * <p>업무 처리가 끝난 메시지만 ACK하고, 복구 가능한 일시 오류는 Retry Queue로,
 * 동일 입력으로 회복할 수 없는 영구 오류는 DLQ로 분리</p>
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

    /**
     * 메시지 처리 결과에 따라 원본 메시지의 최종 소유권을 ACK, 재큐잉 또는 DLQ로 결정
     */
    @RabbitListener(queues = StrategyGenerationMessagingProperties.MAIN_QUEUE)
    public void consume(Message rawMessage, Channel channel) throws IOException {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        StrategyGenerationJobMessage jobMessage = null;

        try {
            int retryCount = readRetryCount(rawMessage);
            jobMessage = deserialize(rawMessage);
            putLogContext(jobMessage);

            jobHandler.handle(jobMessage);
            // DB와 외부 저장소 처리가 모두 끝난 뒤에만 원본 메시지 제거
            channel.basicAck(deliveryTag, false);
            log.info(
                    "AI strategy generation message handled. retryCount={}",
                    retryCount
            );
        } catch (StrategyGenerationBusyException exception) {
            handleBusy(rawMessage, jobMessage, channel, deliveryTag, exception);
        } catch (PermanentStrategyGenerationException exception) {
            recordFailure(
                    jobMessage,
                    exception.getExpectedStage(),
                    exception.getFailureCode(),
                    exception.getMessage()
            );
            log.error("Permanent AI strategy message failure; routing to DLQ", exception);
            // 동일 메시지를 반복해도 회복되지 않는 오류이므로 재시도 없이 DLQ 격리
            channel.basicReject(deliveryTag, false);
        } catch (RetryableStrategyGenerationException exception) {
            handleTransientFailure(
                    rawMessage,
                    jobMessage,
                    channel,
                    deliveryTag,
                    exception.getFailureCode(),
                    exception.getExpectedStage(),
                    exception
            );
        } catch (RuntimeException exception) {
            handleTransientFailure(
                    rawMessage,
                    jobMessage,
                    channel,
                    deliveryTag,
                    RETRY_EXHAUSTED_CODE,
                    null,
                    exception
            );
        } finally {
            MDC.remove("messageId");
            MDC.remove("strategyCaseId");
        }
    }

    private StrategyGenerationJobMessage deserialize(Message rawMessage) {
        try {
            StrategyGenerationJobMessage message = objectMapper.readValue(
                    rawMessage.getBody(),
                    StrategyGenerationJobMessage.class
            );
            if (message == null) {
                throw new PermanentStrategyGenerationException(
                        INVALID_MESSAGE_CODE,
                        "AI strategy generation message JSON must not be null"
                );
            }
            return message;
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
            String failureCode,
            StrategyGenerationStage expectedStage,
            RuntimeException exception
    ) throws IOException {
        int retryCount = readRetryCount(rawMessage);
        int nextRetryCount = retryCount + 1;
        if (nextRetryCount < properties.getMaxAttempts()) {
            try {
                // 재시도 메시지 발행 성공 후 ACK해 작업 유실과 원본 중복 보존을 방지
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
                // 대체 메시지를 만들지 못했으므로 Broker가 원본을 다시 전달하도록 요청
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }

        String failureMessage = failureCode == null
                ? exception.getMessage()
                : "[" + failureCode + "] " + exception.getMessage();
        recordFailure(
                jobMessage,
                expectedStage,
                RETRY_EXHAUSTED_CODE,
                failureMessage
        );
        log.error(
                "AI strategy generation retries exhausted; routing to DLQ. attempts={}",
                properties.getMaxAttempts(),
                exception
        );
        channel.basicReject(deliveryTag, false);
    }

    private void handleBusy(
            Message rawMessage,
            StrategyGenerationJobMessage jobMessage,
            Channel channel,
            long deliveryTag,
            StrategyGenerationBusyException exception
    ) throws IOException {
        int retryCount = readRetryCount(rawMessage);
        try {
            // Lock 경합은 외부 API 실패가 아니므로 허용된 API 재시도 횟수를 보존
            retryPublisher.publishForRetry(jobMessage, retryCount);
            channel.basicAck(deliveryTag, false);
            log.info(
                    "AI strategy generation is owned by another worker; delaying without "
                            + "consuming an API retry. retryCount={}",
                    retryCount
            );
        } catch (RuntimeException retryPublishException) {
            log.error(
                    "Failed to delay busy AI strategy message; requeueing original",
                    retryPublishException
            );
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void recordFailure(
            StrategyGenerationJobMessage message,
            StrategyGenerationStage expectedStage,
            String failureCode,
            String failureMessage
    ) {
        if (message == null || message.strategyCaseId() == null) {
            return;
        }
        try {
            boolean updated = failureService.markFailed(
                    message.strategyCaseId(),
                    expectedStage,
                    failureCode,
                    failureMessage
            );
            if (!updated) {
                log.warn(
                        "AI strategy failure was not persisted because case state changed. "
                                + "strategyCaseId={}, expectedStage={}, failureCode={}",
                        message.strategyCaseId(),
                        expectedStage,
                        failureCode
                );
            }
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
