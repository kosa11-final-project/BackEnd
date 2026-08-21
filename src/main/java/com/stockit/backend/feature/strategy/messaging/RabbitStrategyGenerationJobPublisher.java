package com.stockit.backend.feature.strategy.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobPublisher;
import com.stockit.backend.feature.strategy.service.StrategyGenerationRetryPublisher;

/**
 * 전략 생성 작업을 영속 JSON 메시지로 발행하는 RabbitMQ Adapter
 *
 * <p>Broker 수신 여부와 Queue 라우팅 여부를 함께 검증해 발행 호출 성공만으로
 * 작업 전달이 보장됐다고 판단하지 않음</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RabbitStrategyGenerationJobPublisher implements
        StrategyGenerationJobPublisher,
        StrategyGenerationRetryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final StrategyGenerationMessagingProperties properties;

    public RabbitStrategyGenerationJobPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            StrategyGenerationMessagingProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(StrategyGenerationJobMessage message) {
        sendAndConfirm(
                StrategyGenerationMessagingProperties.MAIN_EXCHANGE,
                StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY,
                message,
                0
        );
    }

    @Override
    public void publishForRetry(
            StrategyGenerationJobMessage message,
            int retryCount
    ) {
        sendAndConfirm(
                StrategyGenerationMessagingProperties.RETRY_EXCHANGE,
                StrategyGenerationMessagingProperties.RETRY_ROUTING_KEY,
                message,
                retryCount
        );
    }

    private void sendAndConfirm(
            String exchange,
            String routingKey,
            StrategyGenerationJobMessage payload,
            int retryCount
    ) {
        Message message = createMessage(payload, retryCount);
        CorrelationData correlationData = new CorrelationData(payload.messageId().toString());

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
            // Broker 저장 여부를 확인한 뒤에만 발행 성공으로 간주
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getConfirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!confirm.isAck()) {
                throw new StrategyGenerationPublishException(
                        "RabbitMQ publisher confirm NACK: " + confirm.getReason()
                );
            }
            // Confirm ACK와 별개로 바인딩 실패 메시지가 반환될 수 있어 추가 검증
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new StrategyGenerationPublishException(
                        "RabbitMQ message was not routed: " + returned.getReplyText()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StrategyGenerationPublishException(
                    "Interrupted while waiting for RabbitMQ publisher confirm",
                    exception
            );
        } catch (StrategyGenerationPublishException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StrategyGenerationPublishException(
                    "Failed to publish AI strategy generation message",
                    exception
            );
        }
    }

    private Message createMessage(
            StrategyGenerationJobMessage payload,
            int retryCount
    ) {
        try {
            return MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(payload))
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(payload.messageId().toString())
                    .setHeader(
                            StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER,
                            retryCount
                    )
                    .build();
        } catch (JsonProcessingException exception) {
            throw new StrategyGenerationPublishException(
                    "Failed to serialize AI strategy generation message",
                    exception
            );
        }
    }
}
