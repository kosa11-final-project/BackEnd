package com.stockit.backend.feature.strategy.forecast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.service.StrategySalesPointQuerySupport;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/**
 * 저장된 Case 스냅샷을 외부 ML 요청과 응답 기대 범위로 변환
 */
@Component
public class StrategyForecastRequestFactory {

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyForecastRequestHasher requestHasher;

    public StrategyForecastRequestFactory(
            StrategyCaseMapper strategyCaseMapper,
            StrategyForecastRequestHasher requestHasher
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.requestHasher = requestHasher;
    }

    /**
     * 요청 시점 스냅샷과 현재 활성 판매처를 조합해 ML 요청 및 응답 검증 기준 생성
     */
    public StrategyForecastRequestContext create(
            StrategyCaseVO strategyCase,
            StrategyCaseRequestPayload payload
    ) {
        StrategyGenerationStage expectedStage = strategyCase.getGenerationStage();
        StrategyForecastRequest request = new StrategyForecastRequest(
                strategyCase.getStrategyCaseId(),
                strategyCase.getSkuId(),
                strategyCase.getRequestedSalesPointId(),
                payload.candidateSalesPointIds(),
                payload.forecastStartDate(),
                payload.forecastEndDate()
        );
        validateRequest(request, expectedStage);

        List<Long> expectedSalesPointIds = resolveExpectedSalesPointIds(
                request,
                expectedStage
        );
        return new StrategyForecastRequestContext(
                request,
                expectedSalesPointIds,
                requestHasher.hash(request, expectedStage)
        );
    }

    private List<Long> resolveExpectedSalesPointIds(
            StrategyForecastRequest request,
            StrategyGenerationStage expectedStage
    ) {
        if (request.candidateSalesPointIds().isEmpty()) {
            // 후보 미지정은 Worker 실행 시점의 전체 활성 판매처 예측을 의미
            List<Long> activeIds = new ArrayList<>(
                    strategyCaseMapper.selectAllActiveSalesPointIds()
            );
            activeIds.sort(Long::compareTo);
            if (activeIds.isEmpty()
                    || (request.sourceSalesPointId() != null
                    && !activeIds.contains(request.sourceSalesPointId()))) {
                throw permanent(
                        expectedStage,
                        "No valid active sales point scope exists for demand forecasting"
                );
            }
            return List.copyOf(activeIds);
        }

        Set<Long> requestedIds = new LinkedHashSet<>(
                request.candidateSalesPointIds()
        );
        if (request.sourceSalesPointId() != null) {
            requestedIds.add(request.sourceSalesPointId());
        }
        // 요청 이후 비활성화된 판매처로 전략이 생성되지 않도록 외부 호출 직전 재검증
        List<Long> activeIds = StrategySalesPointQuerySupport.selectActiveSalesPointIds(
                strategyCaseMapper,
                List.copyOf(requestedIds)
        );
        if (activeIds.size() != requestedIds.size()
                || !new HashSet<>(activeIds).containsAll(requestedIds)) {
            throw permanent(
                    expectedStage,
                    "A demand forecast source or candidate sales point is no longer active"
            );
        }
        List<Long> sortedIds = new ArrayList<>(requestedIds);
        sortedIds.sort(Long::compareTo);
        return List.copyOf(sortedIds);
    }

    private static void validateRequest(
            StrategyForecastRequest request,
            StrategyGenerationStage expectedStage
    ) {
        if (request.strategyRequestId() == null
                || request.strategyRequestId() <= 0
                || request.skuId() == null
                || request.skuId() <= 0
                || request.forecastStartDate() == null
                || request.forecastEndDate() == null
                || request.forecastStartDate().isAfter(request.forecastEndDate())) {
            throw new PermanentStrategyGenerationException(
                    "FORECAST_REQUEST_INVALID",
                    expectedStage,
                    "Stored demand forecast request is invalid"
            );
        }
    }

    private static PermanentStrategyGenerationException permanent(
            StrategyGenerationStage expectedStage,
            String message
    ) {
        return new PermanentStrategyGenerationException(
                "FORECAST_TARGET_INVALID",
                expectedStage,
                message
        );
    }
}
