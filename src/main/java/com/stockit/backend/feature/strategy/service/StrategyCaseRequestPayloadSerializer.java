package com.stockit.backend.feature.strategy.service;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;

/**
 * 사용자 선택 순서를 유지한 전략 요청 스냅샷 JSON 직렬화기
 */
@Component
public class StrategyCaseRequestPayloadSerializer {

    private final ObjectMapper objectMapper;

    public StrategyCaseRequestPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Oracle JSON CHECK 제약을 만족하는 요청 payload 문자열 생성
     */
    public String serialize(StrategyCaseRequestPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
