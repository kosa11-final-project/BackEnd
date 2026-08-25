package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.statistics.dto.response.InventoryFinancialStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDataQualityResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.dto.response.RiskGradeStatisticsResponse;

@Component
public class InventoryStatisticsDemoTrendSimulator {

    public InventoryStatisticsSummaryResponse simulate(
            InventoryStatisticsSummaryResponse current,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            double salesActivityIndex,
            String scopeKey
    ) {
        if (date.equals(toDate)) {
            return current;
        }

        long totalDays = Math.max(1, ChronoUnit.DAYS.between(fromDate, toDate));
        double progress = (double) ChronoUnit.DAYS.between(fromDate, date) / totalDays;
        double pastWeight = 1.0 - progress;
        double phase = Math.floorMod(scopeKey.hashCode(), 31) / 31.0 * Math.PI * 2.0;
        double wave = Math.sin(ChronoUnit.DAYS.between(fromDate, date) / 11.0 + phase);
        double salesEffect = clamp(salesActivityIndex, 0.6, 1.4) - 1.0;

        double stockFactor = 1.0 + pastWeight * (0.08 + wave * 0.025);
        double criticalFactor = 1.0 + pastWeight * (0.30 + wave * 0.05 - salesEffect * 0.08);
        double warningFactor = 1.0 + pastWeight * (0.18 - wave * 0.035 - salesEffect * 0.05);
        double disposalFactor = 1.0 + pastWeight * (0.35 + wave * 0.08 - salesEffect * 0.10);
        double shortageFactor = 1.0 + pastWeight * (0.12 - wave * 0.03);

        BigDecimal totalStock = scale(current.totalStockQty(), stockFactor);
        BigDecimal criticalStock = min(
                scale(current.criticalStockQty(), criticalFactor),
                totalStock.multiply(BigDecimal.valueOf(0.55))
        );
        RiskGradeStatisticsResponse currentWarning = grade(current, "WARNING");
        BigDecimal warningStock = min(
                scale(currentWarning.stockQty(), warningFactor),
                totalStock.subtract(criticalStock).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(0.70))
        );
        RiskGradeStatisticsResponse currentUnassessed = grade(current, "UNASSESSED");
        BigDecimal unassessedStock = min(
                scale(currentUnassessed.stockQty(), stockFactor),
                totalStock.subtract(criticalStock).subtract(warningStock).max(BigDecimal.ZERO)
        );
        BigDecimal remainingStock = totalStock.subtract(criticalStock).subtract(warningStock)
                .subtract(unassessedStock).max(BigDecimal.ZERO);
        RiskGradeStatisticsResponse currentNormal = grade(current, "NORMAL");
        RiskGradeStatisticsResponse currentGood = grade(current, "GOOD");
        BigDecimal normalStock = allocate(
                remainingStock,
                currentNormal.stockQty(),
                currentGood.stockQty()
        );
        BigDecimal goodStock = remainingStock.subtract(normalStock);

        long totalSku = scaleCount(current.totalSkuCount(), 1.0 + pastWeight * 0.015);
        long criticalSku = Math.min(scaleCount(current.criticalSkuCount(), criticalFactor), totalSku);
        long warningSku = Math.min(
                scaleCount(currentWarning.skuCount(), warningFactor),
                Math.max(0, totalSku - criticalSku)
        );
        long unassessedSku = Math.min(
                scaleCount(currentUnassessed.skuCount(), stockFactor),
                Math.max(0, totalSku - criticalSku - warningSku)
        );
        long remainingSku = Math.max(0, totalSku - criticalSku - warningSku - unassessedSku);
        long normalSku = allocateCount(remainingSku, currentNormal.skuCount(), currentGood.skuCount());
        long goodSku = remainingSku - normalSku;

        InventoryStatisticsDataQualityResponse quality = current.dataQuality() == null
                ? null
                : new InventoryStatisticsDataQualityResponse(
                        scaleCount(current.dataQuality().unassessedSkuCount(), stockFactor),
                        scale(current.dataQuality().unassessedStockQty(), stockFactor),
                        scaleCount(current.dataQuality().missingForecastSkuCount(), stockFactor),
                        scale(current.dataQuality().missingForecastStockQty(), stockFactor)
                );
        InventoryFinancialStatisticsResponse financial = current.financialSummary() == null
                ? null
                : new InventoryFinancialStatisticsResponse(
                        scale(current.financialSummary().totalInventoryCostAmount(), stockFactor),
                        scale(current.financialSummary().criticalInventoryCostAmount(), criticalFactor),
                        scale(current.financialSummary().expectedDisposalLossAmount30d(), disposalFactor),
                        scaleCount(current.financialSummary().missingCostSkuCount(), stockFactor),
                        scale(current.financialSummary().missingCostStockQty(), stockFactor)
                );

        List<RiskGradeStatisticsResponse> distribution = new ArrayList<>();
        distribution.add(new RiskGradeStatisticsResponse("CRITICAL", criticalSku, criticalStock));
        distribution.add(new RiskGradeStatisticsResponse("WARNING", warningSku, warningStock));
        distribution.add(new RiskGradeStatisticsResponse("NORMAL", normalSku, normalStock));
        distribution.add(new RiskGradeStatisticsResponse("GOOD", goodSku, goodStock));
        distribution.add(new RiskGradeStatisticsResponse("UNASSESSED", unassessedSku, unassessedStock));

        return new InventoryStatisticsSummaryResponse(
                totalSku,
                totalStock,
                min(scale(current.availableStockQty(), stockFactor), totalStock),
                criticalSku,
                criticalStock,
                Math.min(scaleCount(current.shortageSkuCount(), shortageFactor), totalSku),
                scale(current.expectedDisposalQty30d(), disposalFactor),
                distribution,
                quality,
                financial
        );
    }

    private static RiskGradeStatisticsResponse grade(
            InventoryStatisticsSummaryResponse summary,
            String code
    ) {
        if (summary.riskDistribution() == null) {
            return new RiskGradeStatisticsResponse(code, 0, BigDecimal.ZERO);
        }
        return summary.riskDistribution().stream()
                .filter(value -> code.equals(value.riskGrade()))
                .findFirst()
                .orElseGet(() -> new RiskGradeStatisticsResponse(code, 0, BigDecimal.ZERO));
    }

    private static BigDecimal scale(BigDecimal value, double factor) {
        if (value == null || value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(Math.max(0, factor)))
                .setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static long scaleCount(long value, double factor) {
        return Math.max(0, Math.round(value * Math.max(0, factor)));
    }

    private static BigDecimal allocate(BigDecimal total, BigDecimal first, BigDecimal second) {
        BigDecimal left = first == null ? BigDecimal.ZERO : first;
        BigDecimal right = second == null ? BigDecimal.ZERO : second;
        BigDecimal denominator = left.add(right);
        if (denominator.signum() == 0) {
            return total.divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        }
        return total.multiply(left).divide(denominator, 3, RoundingMode.HALF_UP);
    }

    private static long allocateCount(long total, long first, long second) {
        long denominator = first + second;
        return denominator == 0 ? total / 2 : Math.round((double) total * first / denominator);
    }

    private static BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
