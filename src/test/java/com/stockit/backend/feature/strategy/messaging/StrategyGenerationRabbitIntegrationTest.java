package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.service.StrategyGenerationRetryPublisher;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Sql(
        scripts = "/strategy/strategy-case-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyGenerationRabbitIntegrationTest {

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management")
    );

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-strategy.messaging.enabled", () -> true);
        registry.add("app.ai-strategy.messaging.retry-delay", () -> "100ms");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private StrategyCaseMapper strategyCaseMapper;

    @Autowired
    private StrategyCaseService strategyCaseService;

    @Autowired
    private StrategyGenerationRetryPublisher retryPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Test
    void commitsCaseThenPublishesConsumesAndMovesItToForecasting() {
        StrategyCaseCreated created = strategyCaseService.createStrategyCase(
                new CreateStrategyCaseCommand(
                        "RabbitMQ 통합 테스트 전략",
                        101L,
                        10L,
                        List.of(),
                        List.of(),
                        List.of(StrategyType.PRICE_DISCOUNT),
                        null,
                        null
                ),
                99L
        );

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            StrategyCaseVO updated = strategyCaseMapper.selectStrategyCaseById(
                    created.strategyCaseId()
            );
            assertThat(updated.getGenerationStage())
                    .isEqualTo(StrategyGenerationStage.FORECASTING);
        });
    }

    @Test
    void retryQueueReturnsMessageToMainQueueAfterTtl() {
        listenerRegistry.stop();
        rabbitAdmin.purgeQueue(StrategyGenerationMessagingProperties.MAIN_QUEUE, false);
        try {
            StrategyGenerationJobMessage message = jobMessage(12345L);

            retryPublisher.publishForRetry(message, 1);

            Message retried = rabbitTemplate.receive(
                    StrategyGenerationMessagingProperties.MAIN_QUEUE,
                    Duration.ofSeconds(5).toMillis()
            );
            assertThat(retried).isNotNull();
            assertThat(retried.getMessageProperties().getHeaders())
                    .containsEntry(
                            StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER,
                            1
                    );
            assertThat(retried.getMessageProperties().getMessageId())
                    .isEqualTo(message.messageId().toString());
        } finally {
            listenerRegistry.start();
        }
    }

    @Test
    void deadLetterExchangeRoutesFinalFailureToDlq() {
        rabbitAdmin.purgeQueue(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                false
        );
        Message failed = MessageBuilder
                .withBody("failed".getBytes(StandardCharsets.UTF_8))
                .build();

        rabbitTemplate.send(
                StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE,
                StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY,
                failed
        );

        assertThat(rabbitTemplate.receive(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                Duration.ofSeconds(5).toMillis()
        )).isNotNull();
    }

    private static StrategyGenerationJobMessage jobMessage(Long strategyCaseId) {
        return new StrategyGenerationJobMessage(
                StrategyGenerationJobMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                strategyCaseId,
                OffsetDateTime.of(
                        2026,
                        8,
                        20,
                        14,
                        30,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                )
        );
    }
}
