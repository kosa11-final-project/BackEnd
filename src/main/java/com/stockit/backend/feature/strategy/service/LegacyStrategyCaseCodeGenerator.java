package com.stockit.backend.feature.strategy.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 기존 {@code case_code NOT NULL UNIQUE} 제약을 충족하기 위한 내부 호환값 생성기
 *
 * <p>외부 식별자는 {@code strategy_case_id}이며 이 값은 API나 화면에 노출하지 않음</p>
 */
@Component
public class LegacyStrategyCaseCodeGenerator {

    private static final String PREFIX = "SC-";

    /**
     * 순번 조회 없이 동시 요청 간 충돌 가능성을 낮춘 임시 코드 생성
     */
    public String generate() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
