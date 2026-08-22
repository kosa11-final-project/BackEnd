package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RabbitStrategyGenerationJobPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitStrategyGenerationJobPublisher publisher;

    @BeforeEach
    void setUp() {
        StrategyGenerationMessagingProperties properties =
                new StrategyGenerationMessagingProperties();
        properties.setConfirmTimeout(Duration.ofSeconds(1));
        publisher = new RabbitStrategyGenerationJobPublisher(
                rabbitTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
    }

    @Test
    void publishesPersistentMainMessageAfterBrokerAck() {
        completeConfirm(true, null);
        StrategyGenerationJobMessage payload = message();

        publisher.publish(payload);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(StrategyGenerationMessagingProperties.MAIN_EXCHANGE),
                eq(StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class)
        );
        Message published = messageCaptor.getValue();
        assertThat(published.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(published.getMessageProperties().getMessageId())
                .isEqualTo(payload.messageId().toString());
        assertThat(published.getMessageProperties().getHeaders())
                .containsEntry(StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER, 0);
    }

    @Test
    void rejectsPublisherNack() {
        completeConfirm(false, "broker rejected message");

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(StrategyGenerationPublishException.class)
                .hasMessageContaining("NACK");
    }

    @Test
    void rejectsMessageReturnedBecauseNoQueueWasRouted() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    invocation.getArgument(2),
                    312,
                    "NO_ROUTE",
                    StrategyGenerationMessagingProperties.MAIN_EXCHANGE,
                    StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any());

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(StrategyGenerationPublishException.class)
                .hasMessageContaining("not routed");
    }

    @Test
    void retainsMessageIdentityWhenPublishingRetry() {
        completeConfirm(true, null);
        StrategyGenerationJobMessage payload = message();

        publisher.publishForRetry(payload, 2);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(StrategyGenerationMessagingProperties.RETRY_EXCHANGE),
                eq(StrategyGenerationMessagingProperties.RETRY_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class)
        );
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo(payload.messageId().toString());
        assertThat(messageCaptor.getValue().getMessageProperties().getHeaders())
                .containsEntry(StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER, 2);
    }

    private void completeConfirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any());
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
