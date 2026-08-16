package com.stockit.backend.feature.demandforecast.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stockit.backend.common.persistence.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DemandForecastVO extends BaseEntity {

    private Long forecastId;
    private Long skuId;
    private Long salesPointId;
    private Long modelVersionId;
    private LocalDate baseDate;
    private BigDecimal predictedQtyD7;
    private BigDecimal predictedQtyD14;
    private BigDecimal predictedQtyD30;
    private BigDecimal predictedQtyD60;
    private BigDecimal predictedQtyD90;
    private String forecastSource;
    private String confidenceLevel;

    public static DemandForecastVO forImport(
            Long skuId,
            Long salesPointId,
            Long modelVersionId,
            LocalDate baseDate,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal predictedQtyD60,
            BigDecimal predictedQtyD90,
            String forecastSource,
            String confidenceLevel,
            Long userId
    ) {
        DemandForecastVO forecast = new DemandForecastVO();
        forecast.setSkuId(skuId);
        forecast.setSalesPointId(salesPointId);
        forecast.setModelVersionId(modelVersionId);
        forecast.setBaseDate(baseDate);
        forecast.setPredictedQtyD7(predictedQtyD7);
        forecast.setPredictedQtyD14(predictedQtyD14);
        forecast.setPredictedQtyD30(predictedQtyD30);
        forecast.setPredictedQtyD60(predictedQtyD60);
        forecast.setPredictedQtyD90(predictedQtyD90);
        forecast.setForecastSource(forecastSource);
        forecast.setConfidenceLevel(confidenceLevel);
        forecast.setCreatedBy(userId);
        forecast.setUpdatedBy(userId);
        return forecast;
    }
}
