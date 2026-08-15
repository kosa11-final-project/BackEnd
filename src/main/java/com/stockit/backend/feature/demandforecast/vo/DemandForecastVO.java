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
}
