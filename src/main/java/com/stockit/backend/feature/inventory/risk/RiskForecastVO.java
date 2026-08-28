package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 위험 판정에 필요한 동일 forecast 행의 최소 필드 묶음입니다. */
public class RiskForecastVO {

    private LocalDate baseDate;
    private BigDecimal predictedQtyD7;
    private BigDecimal predictedQtyD14;
    private BigDecimal predictedQtyD30;
    private BigDecimal predictedQtyD60;
    private BigDecimal predictedQtyD90;

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(LocalDate baseDate) {
        this.baseDate = baseDate;
    }

    public BigDecimal getPredictedQtyD7() {
        return predictedQtyD7;
    }

    public void setPredictedQtyD7(BigDecimal predictedQtyD7) {
        this.predictedQtyD7 = predictedQtyD7;
    }

    public BigDecimal getPredictedQtyD14() {
        return predictedQtyD14;
    }

    public void setPredictedQtyD14(BigDecimal predictedQtyD14) {
        this.predictedQtyD14 = predictedQtyD14;
    }

    public BigDecimal getPredictedQtyD30() {
        return predictedQtyD30;
    }

    public void setPredictedQtyD30(BigDecimal predictedQtyD30) {
        this.predictedQtyD30 = predictedQtyD30;
    }

    public BigDecimal getPredictedQtyD60() {
        return predictedQtyD60;
    }

    public void setPredictedQtyD60(BigDecimal predictedQtyD60) {
        this.predictedQtyD60 = predictedQtyD60;
    }

    public BigDecimal getPredictedQtyD90() {
        return predictedQtyD90;
    }

    public void setPredictedQtyD90(BigDecimal predictedQtyD90) {
        this.predictedQtyD90 = predictedQtyD90;
    }
}
