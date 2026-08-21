package com.stockit.backend.feature.strategy.forecast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 동일 Case의 수요예측 중복 실행을 막는 Redis 기반 분산 Lock 관리자
 *
 * <p>Lock마다 소유 토큰을 발급하고 소유자가 일치할 때만 삭제해, 만료 후 Lock을
 * 획득한 다른 Worker의 실행권을 이전 Worker가 해제하지 못하도록 보호</p>
 */
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

    /**
     * 원자적 {@code SET NX}와 TTL로 단일 Worker의 제한된 실행권 획득
     */
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

    /**
     * 조회와 삭제 사이의 경쟁 조건을 피하기 위해 소유 토큰 비교와 삭제를 원자적으로 수행
     */
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
