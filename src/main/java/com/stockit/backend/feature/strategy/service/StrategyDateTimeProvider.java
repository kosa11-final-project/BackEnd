package com.stockit.backend.feature.strategy.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * 전략 생성 정책에서 사용하는 업무 기준 시각 제공자
 */
@Component
public class StrategyDateTimeProvider {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 서버 환경의 기본 시간대와 무관한 Asia/Seoul 기준 현재 시각 반환
     */
    public LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
