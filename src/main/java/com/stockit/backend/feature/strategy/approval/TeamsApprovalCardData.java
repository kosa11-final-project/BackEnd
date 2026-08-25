package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Redis 또는 Oracle 원천과 무관한 Teams 최종 전략 카드 데이터. */
public record TeamsApprovalCardData(
        String caseName,
        String skuCode,
        String skuName,
        String requesterName,
        String optionName,
        String recommendationReason,
        List<String> strategyTypes,
        List<String> targetSalesPointNames,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal targetQuantity,
        List<BigDecimal> discountRates,
        BigDecimal strategyPrice,
        BigDecimal expectedSalesQty,
        BigDecimal expectedRevenue,
        BigDecimal totalContributionMargin,
        BigDecimal expectedRemainingQty
) {
    public TeamsApprovalCardData {
        strategyTypes = List.copyOf(strategyTypes);
        targetSalesPointNames = List.copyOf(targetSalesPointNames);
        discountRates = List.copyOf(discountRates);
    }
}
