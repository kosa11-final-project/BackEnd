package com.stockit.backend.feature.demandforecast.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SKU 및 판매처별 수요예측과 예상 재고 조회 응답 DTO입니다.
 *
 * @param status 데이터 가용 상태 (AVAILABLE, NO_DATA, STALE, ERROR 등)
 * @param scope 조회 범위 (SALES_POINT, SKU_AGGREGATE)
 * @param skuCode 상품 SKU 코드
 * @param skuName 상품 SKU 명
 * @param salesPointCode 판매처 코드
 * @param salesPointName 판매처 명
 * @param baseDate 예측 기준일
 * @param modelVersion 모델 버전
 * @param forecastSource 예측 원천
 * @param confidence 예측 신뢰수준 수치
 * @param confidenceLevel 예측 신뢰수준 등급 (HIGH, MEDIUM, LOW)
 * @param availableQty 현재 가용 재고
 * @param safetyStockQty 안전재고 목표치 (미설정 시 null)
 * @param cumulativeForecast 구간별 누적 수요예측 DTO
 * @param projectedInventories 예측 시점별 예상 재고 잔고 DTO
 * @param freshness 데이터 최신성 정보 DTO
 */
@Schema(description = "SKU·판매처별 수요예측 및 예상 잔고 응답")
public record DemandForecastResponse(
        @Schema(description = "조회 및 데이터 상태 (AVAILABLE, NO_DATA, STALE, ERROR)", example = "AVAILABLE")
        String status,

        @Schema(description = "조회 범위 (SALES_POINT, SKU_AGGREGATE)", example = "SALES_POINT")
        String scope,

        @Schema(description = "상품 SKU 코드", example = "SKU-4D82A9F1")
        String skuCode,

        @Schema(description = "상품 SKU명", example = "신선 밀키트 세트")
        String skuName,

        @Schema(description = "판매처 코드 (집계 시 ALL)", example = "GREETING")
        String salesPointCode,

        @Schema(description = "판매처명", example = "그리팅몰")
        String salesPointName,

        @Schema(description = "수요예측 기준일", example = "2026-08-16")
        LocalDate baseDate,

        @Schema(description = "ML 모델 버전", example = "v2.1.0-lgbm")
        String modelVersion,

        @Schema(description = "수요예측 원천", example = "AZURE_ML")
        String forecastSource,

        @Schema(description = "예측 신뢰수준 (Confidence Level)", example = "0.95")
        BigDecimal confidence,

        @Schema(description = "모델이 제공한 신뢰수준 등급", example = "HIGH")
        String confidenceLevel,

        @Schema(description = "현재 가용재고", example = "110")
        BigDecimal availableQty,

        @Schema(description = "적용 중인 안전재고 목표치. 정책이 없으면 null이며 forecast 조회는 계속 제공됩니다.", example = "30", nullable = true)
        BigDecimal safetyStockQty,

        @Schema(description = "구간별 누적 수요예측")
        CumulativeForecastDto cumulativeForecast,

        @Schema(description = "예측 시점별 예상 가용재고")
        ProjectedInventoryDto projectedInventories,

        @Schema(description = "데이터 최신성 및 상태")
        FreshnessDto freshness
)
{
}
