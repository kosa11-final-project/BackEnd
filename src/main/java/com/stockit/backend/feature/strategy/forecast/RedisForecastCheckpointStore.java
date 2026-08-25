package com.stockit.backend.feature.strategy.forecast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * ML 수요예측 성공 결과를 Redis에 임시 보존하는 체크포인트 저장소
 *
 * <p>Case ID뿐 아니라 요청 해시와 기대 판매처 범위까지 검증해 변경된 요청에
 * 과거 예측 결과가 잘못 재사용되는 상황을 차단</p>
 */
@Component
public class RedisForecastCheckpointStore implements ForecastCheckpointStore {

    private static final String KEY_FORMAT = "ai-strategy:case:%d:forecast:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StrategyForecastProperties properties;

    public RedisForecastCheckpointStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StrategyForecastProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().disable(
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        ).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.properties = properties;
    }

    /**
     * 현재 요청과 동일한 무결성 정보를 가진 체크포인트만 복구 대상으로 반환
     */
    @Override
    public Optional<ForecastCheckpoint> find(
            Long strategyCaseId,
            String expectedRequestHash,
            List<Long> expectedSalesPointIds
    ) {
        try {
            String json = redisTemplate.opsForValue().get(key(strategyCaseId));
            if (json == null) {
                return Optional.empty();
            }
            ForecastCheckpoint checkpoint = deserialize(json);
            validate(
                    checkpoint,
                    strategyCaseId,
                    expectedRequestHash,
                    expectedSalesPointIds
            );
            return Optional.of(checkpoint);
        } catch (InvalidForecastCheckpointException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ForecastCheckpointAccessException(
                    "Failed to read demand forecast checkpoint",
                    exception
            );
        }
    }

    /**
     * 승인 전 임시 결과의 수명만 유지하도록 설정된 TTL과 함께 체크포인트 저장
     */
    @Override
    public void save(ForecastCheckpoint checkpoint) {
        try {
            String json = objectMapper.writeValueAsString(checkpoint);
            redisTemplate.opsForValue().set(
                    key(checkpoint.strategyCaseId()),
                    json,
                    properties.getResultTtl()
            );
        } catch (JsonProcessingException exception) {
            throw new InvalidForecastCheckpointException(
                    "Failed to serialize demand forecast checkpoint",
                    exception
            );
        } catch (DataAccessException exception) {
            throw new ForecastCheckpointAccessException(
                    "Failed to save demand forecast checkpoint",
                    exception
            );
        }
    }

    public static String key(Long strategyCaseId) {
        return KEY_FORMAT.formatted(strategyCaseId);
    }

    private ForecastCheckpoint deserialize(String json) {
        try {
            return objectMapper.readValue(json, ForecastCheckpoint.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidForecastCheckpointException(
                    "Demand forecast checkpoint JSON is invalid",
                    exception
            );
        }
    }

    private static void validate(
            ForecastCheckpoint checkpoint,
            Long strategyCaseId,
            String expectedRequestHash,
            List<Long> expectedSalesPointIds
    ) {
        if (checkpoint == null
                || checkpoint.schemaVersion() != ForecastCheckpoint.CURRENT_SCHEMA_VERSION
                || !Objects.equals(checkpoint.strategyCaseId(), strategyCaseId)
                || !Objects.equals(checkpoint.requestHash(), expectedRequestHash)
                || !Objects.equals(
                        checkpoint.expectedSalesPointIds(),
                        expectedSalesPointIds
                )
                || checkpoint.storedAt() == null
                || checkpoint.forecastResponse() == null) {
            throw new InvalidForecastCheckpointException(
                    "Demand forecast checkpoint integrity validation failed"
            );
        }
    }
}
