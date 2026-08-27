package com.stockit.backend.feature.strategy.service;

import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;

/**
 * AI 전략 생성 요청의 검증과 최초 저장을 담당하는 서비스
 */
public interface StrategyCaseService {

    /**
     * 사용자 선택값과 참조 대상을 검증한 뒤 생성 중 상태의 전략 요청 저장
     */
    StrategyCaseCreated createStrategyCase(CreateStrategyCaseCommand command, Long requesterId);

    /** 검증된 실패 Case를 부모로 연결하여 새로운 생성 실행 단위를 저장한다. */
    StrategyCaseCreated createRetryStrategyCase(
            CreateStrategyCaseCommand command,
            Long requesterId,
            Long retryParentCaseId,
            LocalDateTime requestedAt
    );
}
