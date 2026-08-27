package com.stockit.backend.feature.strategy.result;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Component
@EnableConfigurationProperties(StrategyResultProperties.class)
public class RedisStrategyResultStore implements StrategyResultStore {

    private static final String KEY_FORMAT = "ai-strategy:case:%d:result:v2";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StrategyResultProperties properties;

    public RedisStrategyResultStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StrategyResultProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.properties = properties;
    }

    @Override
    public Optional<StrategyGenerationResult> find(Long strategyCaseId) {
        try {
            String json = redisTemplate.opsForValue().get(key(strategyCaseId));
            if (json == null) return Optional.empty();
            StrategyGenerationResult result = objectMapper.readValue(
                    json, StrategyGenerationResult.class
            );
            if (result == null || !strategyCaseId.equals(result.strategyCaseId())) {
                throw new InvalidStrategyResultException(
                        "AI strategy result cache integrity validation failed", null
                );
            }
            return Optional.of(result);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidStrategyResultException(
                    "AI strategy result cache JSON is invalid", exception
            );
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to read AI strategy result cache", exception
            );
        }
    }

    @Override
    public StrategyResultCacheEntry save(StrategyGenerationResult result) {
        try {
            String cacheKey = key(result.strategyCaseId());
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result), properties.getTtl()
            );
            return new StrategyResultCacheEntry(
                    cacheKey, result.generatedAt().plus(properties.getTtl())
            );
        } catch (JsonProcessingException exception) {
            throw new InvalidStrategyResultException(
                    "Failed to serialize AI strategy result", exception
            );
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to save AI strategy result cache", exception
            );
        }
    }

    public static String key(Long strategyCaseId) {
        return KEY_FORMAT.formatted(strategyCaseId);
    }
}
