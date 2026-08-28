package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RiskAssessmentInput(
        String skuCode,
        String salesPointCode,
        BigDecimal onHandQty,
        BigDecimal predictedQtyD7,
        BigDecimal predictedQtyD14,
        BigDecimal predictedQtyD30,
        BigDecimal safetyStockQty,
        LocalDate baseDate,
        List<LotRiskItem> lots,
        boolean forecastAvailable,
        boolean forecastStale,
        LocalDate assessmentDate,
        BigDecimal predictedQtyD60,
        BigDecimal predictedQtyD90,
        BigDecimal reservedQty,
        String forecastUsability,
        boolean extendedForecastProvided
) {
    /**
     * Full v1.7 input. D60/D90 are cumulative values from the same forecast row.
     * reservedQty is optional for old callers; the canonical available quantity is
     * still based on sellable on-hand quantity.
     */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal predictedQtyD60,
            BigDecimal predictedQtyD90,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale,
            LocalDate assessmentDate
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, predictedQtyD14, predictedQtyD30,
                safetyStockQty, baseDate, lots, forecastAvailable, forecastStale, assessmentDate,
                predictedQtyD60, predictedQtyD90, null, null, true);
    }

    /** Full v1.7 input with an explicit reserved quantity. */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal reservedQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal predictedQtyD60,
            BigDecimal predictedQtyD90,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale,
            LocalDate assessmentDate
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, predictedQtyD14, predictedQtyD30,
                safetyStockQty, baseDate, lots, forecastAvailable, forecastStale, assessmentDate,
                predictedQtyD60, predictedQtyD90, reservedQty, null, true);
    }

    /** Full v1.7 input where upstream already classified forecast usability. */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal predictedQtyD60,
            BigDecimal predictedQtyD90,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale,
            LocalDate assessmentDate,
            String forecastUsability
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, predictedQtyD14, predictedQtyD30,
                safetyStockQty, baseDate, lots, forecastAvailable, forecastStale, assessmentDate,
                predictedQtyD60, predictedQtyD90, null, forecastUsability, true);
    }

    /** Existing v1.6 canonical constructor retained for callers compiled against it. */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale,
            LocalDate assessmentDate
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, predictedQtyD14, predictedQtyD30,
                safetyStockQty, baseDate, lots, forecastAvailable, forecastStale, assessmentDate,
                predictedQtyD30, predictedQtyD30, null, null, false);
    }
    /** 기존 호출부와의 호환을 유지하면서 판정 기준일을 예측 기준일로 초기화합니다. */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD30,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, compatibleD14(predictedQtyD7, predictedQtyD30),
                predictedQtyD30, safetyStockQty,
                baseDate, lots, forecastAvailable, forecastStale, baseDate, predictedQtyD30, predictedQtyD30, null, null, false);
    }

    /** 기존 11개 인자 호출부와의 호환을 유지합니다. 실제 조회 경로는 D+14 원본 값을 전달합니다. */
    public RiskAssessmentInput(
            String skuCode,
            String salesPointCode,
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD30,
            BigDecimal safetyStockQty,
            LocalDate baseDate,
            List<LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale,
            LocalDate assessmentDate
    ) {
        this(skuCode, salesPointCode, onHandQty, predictedQtyD7, compatibleD14(predictedQtyD7, predictedQtyD30),
                predictedQtyD30, safetyStockQty, baseDate, lots, forecastAvailable, forecastStale, assessmentDate,
                predictedQtyD30, predictedQtyD30, null, null, false);
    }

    private static BigDecimal compatibleD14(BigDecimal predictedQtyD7, BigDecimal predictedQtyD30) {
        if (predictedQtyD7 == null || predictedQtyD30 == null
                || predictedQtyD30.compareTo(predictedQtyD7) < 0) {
            return null;
        }
        return predictedQtyD7.add(
                predictedQtyD30.subtract(predictedQtyD7)
                        .multiply(BigDecimal.valueOf(7))
                        .divide(BigDecimal.valueOf(23), 6, java.math.RoundingMode.HALF_UP)
        );
    }

    public record LotRiskItem(
            String lotId,
            String lotNumber,
            LocalDate expiryDate,
            LocalDate saleStopDate,
            LocalDate receivedDate,
            BigDecimal quantity,
            String lotStatus,
            BigDecimal reservedQty
    ) {
        /** 기존 호출부와 fixture의 6개 인자 계약을 유지합니다. */
        public LotRiskItem(
                String lotId,
                String lotNumber,
                LocalDate expiryDate,
                LocalDate saleStopDate,
                LocalDate receivedDate,
                BigDecimal quantity
        ) {
            this(lotId, lotNumber, expiryDate, saleStopDate, receivedDate, quantity, null, null);
        }

        /** Existing seven-argument source-status constructor retained for compatibility. */
        public LotRiskItem(
                String lotId,
                String lotNumber,
                LocalDate expiryDate,
                LocalDate saleStopDate,
                LocalDate receivedDate,
                BigDecimal quantity,
                String lotStatus
        ) {
            this(lotId, lotNumber, expiryDate, saleStopDate, receivedDate, quantity, lotStatus, null);
        }
    }

    /** Returns an explicit upstream forecast classification when supplied. */
    public String normalizedForecastUsability() {
        if (forecastUsability == null || forecastUsability.isBlank()) {
            return null;
        }
        return forecastUsability.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
