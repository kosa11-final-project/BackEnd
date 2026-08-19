package com.stockit.backend.feature.demandforecast.service;

import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastResponse;

/**
 * 수요예측 비즈니스 서비스 인터페이스입니다.
 */
public interface DemandForecastService {

    /**
     * FastAPI 서버로부터 전달받은 수요예측 배치 결과를 적재합니다.
     *
     * @param request 수요예측 적재 요청 DTO
     * @param userId 요청 사용자 ID
     * @return 적재 결과 응답 DTO
     */
    DemandForecastImportResponse importForecasts(DemandForecastImportRequest request, Long userId);

    /**
     * SKU 및 판매처별 수요예측과 예상 재고를 조회합니다.
     *
     * @param skuCode 상품 SKU 코드
     * @param salesPointCode 판매처 코드
     * @return 수요예측 응답 DTO
     */
    DemandForecastResponse getForecast(String skuCode, String salesPointCode);

    /**
     * SKU 전체에 대한 통합 수요예측과 예상 재고를 조회합니다.
     *
     * @param skuCode 상품 SKU 코드
     * @return 수요예측 응답 DTO
     */
    DemandForecastResponse getSkuAggregateForecast(String skuCode);
}
