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
 * persistent JSON 메시지를 발행하고 broker confirm과 routing 결과를 검증
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
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getConfirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!confirm.isAck()) {
                throw new StrategyGenerationPublishException(
                        "RabbitMQ publisher confirm NACK: " + confirm.getReason()
                );
            }
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
