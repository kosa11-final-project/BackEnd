package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.rabbitmq.client.GetResponse;
import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.forecast.DailyForecastPrediction;
import com.stockit.backend.feature.strategy.forecast.ForecastProvider;
import com.stockit.backend.feature.strategy.forecast.RedisForecastCheckpointStore;
import com.stockit.backend.feature.strategy.forecast.SalesPointForecast;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequest;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponse;
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

    private static final Duration RECEIVE_POLL_INTERVAL = Duration.ofMillis(25);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management")
    );

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-strategy.messaging.enabled", () -> true);
        registry.add("app.ai-strategy.messaging.retry-delay", () -> "100ms");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ForecastProvider forecastProvider;

    @Test
    void commitsCaseThenForecastsCachesAndMovesToStrategyGenerating() {
        when(forecastProvider.forecast(any())).thenAnswer(invocation ->
                responseFor(invocation.getArgument(0))
        );
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
                    .isEqualTo(StrategyGenerationStage.STRATEGY_GENERATING);
            assertThat(redisTemplate.hasKey(
                    RedisForecastCheckpointStore.key(created.strategyCaseId())
            )).isTrue();
        });
    }

    @Test
    void retriesTransientForecastFailureAndResumesForecastingStage() {
        AtomicInteger attempts = new AtomicInteger();
        when(forecastProvider.forecast(any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RetryableStrategyGenerationException(
                        "FORECAST_API_UNAVAILABLE",
                        StrategyGenerationStage.FORECASTING,
                        "temporary ML failure"
                );
            }
            return responseFor(invocation.getArgument(0));
        });

        StrategyCaseCreated created = strategyCaseService.createStrategyCase(
                new CreateStrategyCaseCommand(
                        "RabbitMQ 수요예측 재시도 테스트",
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
                    .isEqualTo(StrategyGenerationStage.STRATEGY_GENERATING);
            assertThat(attempts).hasValue(2);
        });
    }

    @Test
    void retryQueueReturnsMessageToMainQueueAfterTtl() {
        try {
            listenerRegistry.stop();
            await().atMost(Duration.ofSeconds(5)).until(
                    () -> !listenerRegistry.isRunning()
            );
            rabbitAdmin.purgeQueue(
                    StrategyGenerationMessagingProperties.RETRY_QUEUE,
                    false
            );
            rabbitAdmin.purgeQueue(
                    StrategyGenerationMessagingProperties.MAIN_QUEUE,
                    false
            );

            StrategyGenerationJobMessage message = jobMessage(12345L);

            retryPublisher.publishForRetry(message, 1);

            GetResponse retried = receiveExpectedMessage(
                    StrategyGenerationMessagingProperties.MAIN_QUEUE,
                    message.messageId().toString(),
                    Duration.ofSeconds(5)
            );
            assertThat(retried).isNotNull();
            assertThat(retried.getProps().getHeaders())
                    .containsEntry(
                            StrategyGenerationMessagingProperties.RETRY_COUNT_HEADER,
                            1
                    );
            assertThat(retried.getProps().getMessageId())
                    .isEqualTo(message.messageId().toString());
        } finally {
            listenerRegistry.start();
        }
    }

    @Test
    void permanentFailureFromMainQueueIsRejectedAndDeadLettered() {
        rabbitAdmin.purgeQueue(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                false
        );
        String messageId = UUID.randomUUID().toString();
        Message permanentlyInvalid = MessageBuilder
                .withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(messageId)
                .build();

        rabbitTemplate.send(
                StrategyGenerationMessagingProperties.MAIN_EXCHANGE,
                StrategyGenerationMessagingProperties.MAIN_ROUTING_KEY,
                permanentlyInvalid
        );

        GetResponse deadLettered = receiveExpectedMessage(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                messageId,
                Duration.ofSeconds(5)
        );
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getProps().getMessageId())
                .isEqualTo(messageId);
    }

    @Test
    void deadLetterExchangeBindingRoutesMessageToDlq() {
        rabbitAdmin.purgeQueue(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                false
        );
        String messageId = UUID.randomUUID().toString();
        Message failed = MessageBuilder
                .withBody("failed".getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .build();

        rabbitTemplate.send(
                StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE,
                StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY,
                failed
        );

        GetResponse deadLettered = receiveExpectedMessage(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                messageId,
                Duration.ofSeconds(5)
        );
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getProps().getMessageId())
                .isEqualTo(messageId);
    }

    @Test
    void unexpectedMessageIsRequeuedInsteadOfAcknowledged() {
        rabbitAdmin.purgeQueue(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                false
        );
        String actualMessageId = UUID.randomUUID().toString();
        Message unexpected = MessageBuilder
                .withBody("unexpected".getBytes(StandardCharsets.UTF_8))
                .setMessageId(actualMessageId)
                .build();

        rabbitTemplate.send(
                StrategyGenerationMessagingProperties.DEAD_LETTER_EXCHANGE,
                StrategyGenerationMessagingProperties.DEAD_LETTER_ROUTING_KEY,
                unexpected
        );

        assertThatThrownBy(() -> receiveExpectedMessage(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                UUID.randomUUID().toString(),
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Unexpected RabbitMQ messageId");

        GetResponse requeued = receiveExpectedMessage(
                StrategyGenerationMessagingProperties.DEAD_LETTER_QUEUE,
                actualMessageId,
                Duration.ofSeconds(5)
        );
        assertThat(requeued).isNotNull();
        assertThat(requeued.getProps().getMessageId())
                .isEqualTo(actualMessageId);
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

    private static StrategyForecastResponse responseFor(
            StrategyForecastRequest request
    ) {
        List<LocalDate> dates = request.forecastStartDate()
                .datesUntil(request.forecastEndDate().plusDays(1))
                .toList();
        List<SalesPointForecast> forecasts = new ArrayList<>();
        for (Long salesPointId : List.of(10L, 20L)) {
            forecasts.add(new SalesPointForecast(
                    salesPointId,
                    salesPointId.equals(request.sourceSalesPointId()),
                    dates.stream()
                            .map(date -> new DailyForecastPrediction(
                                    date,
                                    BigDecimal.ONE
                            ))
                            .toList()
            ));
        }
        return new StrategyForecastResponse(
                request.strategyRequestId(),
                request.skuId(),
                request.sourceSalesPointId(),
                request.candidateSalesPointIds(),
                request.forecastStartDate(),
                request.forecastEndDate(),
                dates.size(),
                "integration-forecast-run",
                1L,
                OffsetDateTime.parse("2026-08-20T10:15:30+09:00"),
                forecasts
        );
    }

    private GetResponse receiveExpectedMessage(
            String queue,
            String expectedMessageId,
            Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            GetResponse candidate = rabbitTemplate.execute(channel -> {
                GetResponse response = channel.basicGet(queue, false);
                if (response == null) {
                    return null;
                }

                long deliveryTag = response.getEnvelope().getDeliveryTag();
                if (expectedMessageId.equals(response.getProps().getMessageId())) {
                    channel.basicAck(deliveryTag, false);
                    return response;
                }

                channel.basicNack(deliveryTag, false, true);
                throw new AssertionError(
                        "Unexpected RabbitMQ messageId. expected="
                                + expectedMessageId
                                + ", actual="
                                + response.getProps().getMessageId()
                );
            });
            if (candidate != null) {
                return candidate;
            }
            LockSupport.parkNanos(RECEIVE_POLL_INTERVAL.toNanos());
        }
        return null;
    }
}
