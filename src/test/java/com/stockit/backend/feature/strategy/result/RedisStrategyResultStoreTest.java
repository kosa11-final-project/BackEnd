package com.stockit.backend.feature.strategy.result;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisStrategyResultStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisStrategyResultStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisStrategyResultStore(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new StrategyResultProperties()
        );
    }

    @Test
    void rejectsJsonNullAsCacheIntegrityFailure() {
        when(valueOperations.get(RedisStrategyResultStore.key(123L)))
                .thenReturn("null");

        assertThatThrownBy(() -> store.find(123L))
                .isInstanceOf(InvalidStrategyResultException.class)
                .hasMessageContaining("integrity validation failed");
    }
}
