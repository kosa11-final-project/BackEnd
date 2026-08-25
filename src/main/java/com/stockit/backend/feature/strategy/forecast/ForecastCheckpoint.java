package com.stockit.backend.feature.strategy.forecast;

import java.time.Instant;
import java.util.List;

/**
 * 외부 API 재호출 없이 FORECASTING 단계를 복구하기 위한 수요예측 결과 스냅샷
 *
 * @param requestHash 현재 Case에서 복원한 ML 요청과 동일한지 확인하는 무결성 값
 * @param expectedSalesPointIds 응답 누락과 과거 범위 재사용을 검증하는 판매처 목록
 * @param modelVersionId 외부 모델명·버전을 DB에서 해석한 내부 모델 버전 PK
 */
public record ForecastCheckpoint(
        int schemaVersion,
        Long strategyCaseId,
        String requestHash,
        List<Long> expectedSalesPointIds,
        Instant storedAt,
        Long modelVersionId,
        StrategyForecastResponse forecastResponse
) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public ForecastCheckpoint {
        expectedSalesPointIds = expectedSalesPointIds == null
                ? List.of()
                : List.copyOf(expectedSalesPointIds);
        if (modelVersionId == null || modelVersionId <= 0) {
            throw new IllegalArgumentException(
                    "Resolved modelVersionId must be positive"
            );
        }
    }

    /**
     * 검증이 완료된 ML 응답과 당시 요청 문맥으로 저장용 체크포인트 생성
     */
    public static ForecastCheckpoint create(
            StrategyForecastRequestContext context,
            StrategyForecastResponse response,
            Long modelVersionId,
            Instant storedAt
    ) {
        return new ForecastCheckpoint(
                CURRENT_SCHEMA_VERSION,
                context.request().strategyRequestId(),
                context.requestHash(),
                context.expectedSalesPointIds(),
                storedAt,
                modelVersionId,
                response
        );
    }
}
