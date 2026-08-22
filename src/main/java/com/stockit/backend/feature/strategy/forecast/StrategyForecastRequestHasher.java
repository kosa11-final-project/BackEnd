package com.stockit.backend.feature.strategy.forecast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;

/**
 * 체크포인트가 현재 ML 요청에서 생성됐는지 판별하기 위한 정규화 요청 해시 생성기
 */
@Component
public class StrategyForecastRequestHasher {

    private final ObjectMapper canonicalObjectMapper;

    public StrategyForecastRequestHasher(ObjectMapper objectMapper) {
        this.canonicalObjectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * 객체와 Map의 필드 순서 차이에 영향받지 않는 SHA-256 요청 식별값 생성
     */
    public String hash(
            StrategyForecastRequest request,
            StrategyGenerationStage expectedStage
    ) {
        try {
            byte[] canonicalJson = canonicalObjectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new PermanentStrategyGenerationException(
                    "FORECAST_REQUEST_INVALID",
                    expectedStage,
                    "Failed to create canonical demand forecast request hash",
                    exception
            );
        }
    }
}
