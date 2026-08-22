package com.stockit.backend.feature.strategy.service;

/**
 * DB에 저장된 AI 전략 생성 요청 스냅샷을 복원할 수 없는 경우
 */
public class StrategyCasePayloadException extends RuntimeException {

    public StrategyCasePayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
