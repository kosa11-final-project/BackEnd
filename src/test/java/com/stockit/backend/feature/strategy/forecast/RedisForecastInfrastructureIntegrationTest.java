package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisForecastInfrastructureIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private StrategyForecastProperties properties;

    @BeforeAll
    void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    void closeRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void reset() {
        connectionFactory.getConnection().serverCommands().flushDb();
        properties = new StrategyForecastProperties();
        properties.setResultTtl(Duration.ofMinutes(2));
        properties.setLockTtl(Duration.ofMillis(150));
    }

    @Test
    void storesValidatedCheckpointWithTtl() {
        RedisForecastCheckpointStore store = store();
        ForecastCheckpoint checkpoint = ForecastCheckpoint.create(
                context(),
                response(),
                Instant.parse("2026-08-20T00:00:00Z")
        );

        store.save(checkpoint);

        assertThat(store.find(12345L, "request-hash", List.of(10L)))
                .contains(checkpoint);
        Long ttl = redisTemplate.getExpire(
                RedisForecastCheckpointStore.key(12345L)
        );
        assertThat(ttl).isPositive().isLessThanOrEqualTo(120L);
    }

    @Test
    void rejectsCheckpointWithDifferentRequestHashAndMalformedJson() {
        RedisForecastCheckpointStore store = store();
        store.save(ForecastCheckpoint.create(
                context(),
                response(),
                Instant.now()
        ));

        assertThatThrownBy(() -> store.find(
                12345L,
                "different-hash",
                List.of(10L)
        )).isInstanceOf(InvalidForecastCheckpointException.class);

        redisTemplate.opsForValue().set(
                RedisForecastCheckpointStore.key(12345L),
                "not-json"
        );
        assertThatThrownBy(() -> store.find(
                12345L,
                "request-hash",
                List.of(10L)
        )).isInstanceOf(InvalidForecastCheckpointException.class);
    }

    @Test
    void onlyOwnerCanReleaseLockAndExpiredLockCanBeReacquired() {
        RedisForecastLockManager manager = new RedisForecastLockManager(
                redisTemplate,
                properties
        );
        ForecastLock acquired = manager.tryAcquire(12345L).orElseThrow();

        assertThat(manager.tryAcquire(12345L)).isEmpty();
        manager.release(new ForecastLock(acquired.key(), "different-owner"));
        assertThat(redisTemplate.hasKey(acquired.key())).isTrue();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(manager.tryAcquire(12345L)).isPresent()
        );
    }

    private RedisForecastCheckpointStore store() {
        return new RedisForecastCheckpointStore(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                properties
        );
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
                3L,
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
}
