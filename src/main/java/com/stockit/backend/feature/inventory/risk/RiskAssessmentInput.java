package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RiskAssessmentInput(
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
    public record LotRiskItem(
            String lotId,
            String lotNumber,
            LocalDate expiryDate,
            LocalDate saleStopDate,
            LocalDate receivedDate,
            BigDecimal quantity,
            String lotStatus
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
            this(lotId, lotNumber, expiryDate, saleStopDate, receivedDate, quantity, null);
        }
    }
}
