package com.stockit.backend.feature.demandforecast.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 전체 배치 수신 전까지 최종 Forecast 조회에서 격리되는 예측 행입니다. */
@Getter
@Setter
public class DemandForecastStagingVO {
    private Long forecastRunId;
    private Integer batchNumber;
    private Long skuId;
    private Long salesPointId;
    private BigDecimal predictedQtyD7;
    private BigDecimal predictedQtyD14;
    private BigDecimal predictedQtyD30;
    private BigDecimal predictedQtyD60;
    private BigDecimal predictedQtyD90;
    private String forecastSource;
    private String confidenceLevel;
    private Long createdBy;
}
