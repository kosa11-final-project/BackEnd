package com.stockit.backend.feature.strategy.forecast;

import java.util.Optional;

/**
 * 중단된 수요예측 단계를 외부 API 재호출 없이 복구하기 위한 체크포인트 Port
 */
public interface ForecastCheckpointStore {

    /**
     * 현재 요청의 해시와 판매처 범위가 일치하는 체크포인트 조회
     */
    Optional<ForecastCheckpoint> find(
            Long strategyCaseId,
            String expectedRequestHash,
            java.util.List<Long> expectedSalesPointIds
    );

    /**
     * 응답 검증이 끝난 수요예측 결과 저장
     */
    void save(ForecastCheckpoint checkpoint);
}
