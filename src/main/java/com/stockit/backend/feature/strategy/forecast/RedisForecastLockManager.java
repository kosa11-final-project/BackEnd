package com.stockit.backend.feature.strategy.forecast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisForecastLockManager implements ForecastLockManager {

    private static final String KEY_FORMAT =
            "ai-strategy:case:%d:lock:forecast";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final StrategyForecastProperties properties;

    public RedisForecastLockManager(
            StringRedisTemplate redisTemplate,
            StrategyForecastProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<ForecastLock> tryAcquire(Long strategyCaseId) {
        String key = KEY_FORMAT.formatted(strategyCaseId);
        String ownerToken = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    ownerToken,
                    properties.getLockTtl()
            );
            return Boolean.TRUE.equals(acquired)
                    ? Optional.of(new ForecastLock(key, ownerToken))
                    : Optional.empty();
        } catch (DataAccessException exception) {
            throw new ForecastLockAccessException(
                    "Failed to acquire demand forecast execution lock",
                    exception
            );
        }
    }

    @Override
    public void release(ForecastLock lock) {
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(lock.key()),
                    lock.ownerToken()
            );
        } catch (DataAccessException exception) {
            throw new ForecastLockAccessException(
                    "Failed to release demand forecast execution lock",
                    exception
            );
        }
    }
}
