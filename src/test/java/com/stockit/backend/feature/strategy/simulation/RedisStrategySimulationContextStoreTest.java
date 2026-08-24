package com.stockit.backend.feature.strategy.simulation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

@ExtendWith(MockitoExtension.class)
class RedisStrategySimulationContextStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisStrategySimulationContextStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisStrategySimulationContextStore(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new StrategyResultProperties()
        );
    }

    @Test
    void rejectsJsonNullAsContextIntegrityFailure() {
        when(valueOperations.get(RedisStrategySimulationContextStore.key(123L)))
                .thenReturn("null");

        assertThatThrownBy(() -> store.find(123L))
                .isInstanceOf(InvalidStrategyResultException.class)
                .hasMessageContaining("integrity validation failed");
    }

    @Test
    void roundTripsCalculationContextWithResultTtl() {
        StrategyCalculationContext context = context();
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        store.save(context);

        verify(valueOperations).set(
                eq(RedisStrategySimulationContextStore.key(123L)),
                json.capture(),
                eq(Duration.ofDays(3))
        );
        when(valueOperations.get(RedisStrategySimulationContextStore.key(123L)))
                .thenReturn(json.getValue());

        assertThat(store.find(123L)).contains(context);
    }

    private static StrategyCalculationContext context() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        StrategyCalculationContext.Price price =
                new StrategyCalculationContext.Price(
                        1L, decimal("120"), decimal("100"), decimal("70"),
                        decimal("5"), decimal("10")
                );
        StrategyCalculationContext.SalesPoint salesPoint =
                new StrategyCalculationContext.SalesPoint(
                        10L, "DEPT_PANGYO", "판교점", BigDecimal.ZERO,
                        true, price, Map.of(date, decimal("2")), List.of()
                );
        StrategyCalculationContext.InventoryLot lot =
                new StrategyCalculationContext.InventoryLot(
                        1L, 1001L, 501L, 10L, 10L, decimal("10"),
                        BigDecimal.ZERO, null, date.minusDays(10), null,
                        null, "AVAILABLE"
                );
        return new StrategyCalculationContext(
                123L, 10L, LocalDateTime.of(2026, 8, 20, 9, 0),
                date, date,
                new StrategyCalculationContext.Sku(
                        100L, "SKU-100", "상품", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                List.of(lot), List.of(lot), List.of(), Map.of(10L, salesPoint),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-1", 1L,
                        OffsetDateTime.of(
                                2026, 8, 20, 8, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
