package com.stockit.backend.feature.demandforecast.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

/**
 * 수요예측 데이터베이스 매퍼 인터페이스입니다.
 */
@Mapper
public interface DemandForecastMapper {

    /**
     * 모델 이름과 버전으로 모델 버전 ID를 조회합니다.
     *
     * @param modelName 모델명
     * @param modelVersion 모델 버전
     * @return 모델 버전 ID (없으면 null)
     */
    Long selectModelVersionId(
            @Param("modelName") String modelName,
            @Param("modelVersion") String modelVersion
    );

    /**
     * 요청된 SKU ID 목록 중 실제 존재하는 SKU의 개수를 조회합니다.
     *
     * @param skuIds SKU ID 목록
     * @return 존재하는 SKU 개수
     */
    int countExistingSkus(@Param("skuIds") List<Long> skuIds);

    /**
     * 요청된 판매처 ID 목록 중 실제 존재하는 판매처의 개수를 조회합니다.
     *
     * @param salesPointIds 판매처 ID 목록
     * @return 존재하는 판매처 개수
     */
    int countExistingSalesPoints(@Param("salesPointIds") List<Long> salesPointIds);

    /**
     * 수요예측 데이터 목록을 일괄 MERGE(INSERT OR UPDATE)합니다.
     *
     * @param forecasts 수요예측 VO 목록
     * @return 처리된 행 수
     */
    int mergeDemandForecasts(@Param("forecasts") List<DemandForecastVO> forecasts);

    /**
     * SKU 코드와 판매처 코드로 가장 최근의 수요예측 단건을 조회합니다.
     *
     * @param skuCode SKU 코드
     * @param salesPointCode 판매처 코드
     * @return 수요예측 VO (없으면 null)
     */
    DemandForecastVO selectDemandForecast(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

    /**
     * 특정 일자 기준 적용되는 안전재고 수량을 조회합니다.
     *
     * @param skuCode SKU 코드
     * @param salesPointCode 판매처 코드
     * @param asOfDate 기준일자
     * @return 안전재고 수량 (없으면 null)
     */
    BigDecimal selectSafetyStockQty(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * SKU와 판매처의 현재 가용재고 수량을 조회합니다.
     *
     * @param skuCode SKU 코드
     * @param salesPointCode 판매처 코드
     * @return 가용재고 수량 (없으면 null)
     */
    BigDecimal selectAvailableQty(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

    /**
     * 특정 일자 기준 SKU 전체에 대한 안전재고 목표치 합계를 조회합니다.
     *
     * @param skuCode SKU 코드
     * @param asOfDate 기준일자
     * @return 안전재고 합계 수량
     */
    BigDecimal selectSkuAggregateSafetyStockQty(
            @Param("skuCode") String skuCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * SKU 전체에 대한 가용재고 합계를 조회합니다.
     *
     * @param skuCode SKU 코드
     * @return 가용재고 합계 수량
     */
    BigDecimal selectSkuAggregateAvailableQty(@Param("skuCode") String skuCode);

    /**
     * 동일 기준일·모델을 가진 판매처들의 수요예측 합계 VO를 조회합니다.
     *
     * @param skuCode SKU 코드
     * @return 합산된 수요예측 VO (불일치하거나 데이터 없으면 null)
     */
    DemandForecastVO selectSkuAggregateDemandForecast(
            @Param("skuCode") String skuCode
    );

}
