package com.stockit.backend.feature.strategy.simulation;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;

/** 조정 시뮬레이션의 재현성을 위해 계산 문맥을 결과와 같은 3일 TTL로 저장한다. */
@Component
public class RedisStrategySimulationContextStore
        implements StrategySimulationContextStore {

    private static final String KEY_FORMAT =
            "ai-strategy:case:%d:simulation-context:v2";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StrategyResultProperties properties;

    public RedisStrategySimulationContextStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StrategyResultProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        this.properties = properties;
    }

    @Override
    public Optional<StrategyCalculationContext> find(Long strategyCaseId) {
        try {
            String json = redisTemplate.opsForValue().get(key(strategyCaseId));
            if (json == null) return Optional.empty();
            StrategyCalculationContext context = objectMapper.readValue(
                    json,
                    StrategyCalculationContext.class
            );
            if (context == null
                    || !strategyCaseId.equals(context.strategyCaseId())) {
                throw new InvalidStrategyResultException(
                        "AI strategy simulation context integrity validation failed",
                        null
                );
            }
            return Optional.of(context);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidStrategyResultException(
                    "AI strategy simulation context JSON is invalid",
                    exception
            );
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to read AI strategy simulation context",
                    exception
            );
        }
    }

    @Override
    public void save(StrategyCalculationContext context) {
        try {
            redisTemplate.opsForValue().set(
                    key(context.strategyCaseId()),
                    objectMapper.writeValueAsString(context),
                    properties.getTtl()
            );
        } catch (JsonProcessingException exception) {
            throw new InvalidStrategyResultException(
                    "Failed to serialize AI strategy simulation context",
                    exception
            );
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to save AI strategy simulation context",
                    exception
            );
        }
    }

    public static String key(Long strategyCaseId) {
        return KEY_FORMAT.formatted(strategyCaseId);
    }
}
