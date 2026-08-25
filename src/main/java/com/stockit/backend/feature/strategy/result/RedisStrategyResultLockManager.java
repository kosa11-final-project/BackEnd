package com.stockit.backend.feature.strategy.result;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisStrategyResultLockManager implements StrategyResultLockManager {

    private static final String KEY_FORMAT = "ai-strategy:case:%d:lock:result";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final StrategyResultProperties properties;

    public RedisStrategyResultLockManager(
            StringRedisTemplate redisTemplate,
            StrategyResultProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<StrategyResultLock> tryAcquire(Long strategyCaseId) {
        String key = KEY_FORMAT.formatted(strategyCaseId);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key, token, properties.getLockTtl()
            );
            return Boolean.TRUE.equals(acquired)
                    ? Optional.of(new StrategyResultLock(key, token))
                    : Optional.empty();
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to acquire AI strategy result lock", exception
            );
        }
    }

    @Override
    public void release(StrategyResultLock lock) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lock.key()), lock.ownerToken());
        } catch (DataAccessException exception) {
            throw new StrategyResultStoreException(
                    "Failed to release AI strategy result lock", exception
            );
        }
    }
}
