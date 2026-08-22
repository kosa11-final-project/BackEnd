package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

final class StrategyStatisticsDemoDataFactory {
    static final long CASE_ID_BASE = 970_000_000_000L;
    static final long OPTION_ID_BASE = 971_000_000_000L;
    static final long SIMULATION_ID_BASE = 972_000_000_000L;
    static final long SELECTION_ID_BASE = 973_000_000_000L;
    static final long RESULT_ID_BASE = 974_000_000_000L;
    static final long ACTION_ID_BASE = 975_000_000_000L;

    private static final List<List<String>> ACTION_COMBINATIONS = List.of(
            List.of("PRICE_DISCOUNT"),
            List.of("REALLOCATION"),
            List.of("CHANNEL_EXPANSION"),
            List.of("PRICE_DISCOUNT", "CHANNEL_EXPANSION"),
            List.of("REALLOCATION", "PRICE_DISCOUNT"),
            List.of("RT_TRANSFER", "PRICE_DISCOUNT", "CHANNEL_EXPANSION")
    );
    private static final double[] ACHIEVEMENT_BONUS = {0, 3, -2, 7, 6, 10};
    private static final double[] RISK_REDUCTION_RATE = {0.20, 0.36, 0.26, 0.43, 0.47, 0.55};
    private static final double[] DISPOSAL_REDUCTION_RATE = {0.32, 0.27, 0.38, 0.47, 0.44, 0.58};

    List<StrategyStatisticsDemoData> create(
            LocalDate fromDate,
            LocalDate toDate,
            StrategyStatisticsDemoDimensions dimensions
    ) {
        List<StrategyStatisticsDemoData> results = new ArrayList<>();
        int dayIndex = 0;
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            int dailyCount = dailyStrategyCount(date);
            for (int ordinal = 0; ordinal < dailyCount; ordinal++) {
                results.add(createOne(date, dayIndex, ordinal, dimensions));
            }
            dayIndex++;
        }
        return results;
    }

    private static StrategyStatisticsDemoData createOne(
            LocalDate endDate,
            int dayIndex,
            int ordinal,
            StrategyStatisticsDemoDimensions dimensions
    ) {
        long dayKey = endDate.toEpochDay();
        long rowKey = dayKey * 10L + ordinal;
        long seed = dayKey * 37L + ordinal * 101L;
        int combinationIndex = Math.floorMod(dayIndex * 3 + ordinal, ACTION_COMBINATIONS.size());
        List<String> actionTypes = ACTION_COMBINATIONS.get(combinationIndex);

        LocalDate startDate = endDate.minusDays(7L + Math.floorMod(seed, 15));
        LocalDateTime createdAt = startDate.minusDays(2).atTime(LocalTime.of(9, 0));
        LocalDateTime completedAt = endDate.atTime(LocalTime.of(4, 30));
        long skuId = at(dimensions.skuIds(), seed);
        long targetSalesPointId = targetSalesPoint(actionTypes, seed, dimensions);

        BigDecimal target = decimal(45 + Math.floorMod(seed * 13, 210), 3);
        double seasonal = Math.sin(dayIndex / 17.0) * 5.0;
        double noise = Math.floorMod(seed * 19, 900) / 100.0 - 4.5;
        BigDecimal achievementRate = decimal(
                88 + ACHIEVEMENT_BONUS[combinationIndex] + seasonal + noise,
                6
        );
        BigDecimal actual = target.multiply(achievementRate)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);

        BigDecimal startRisk = target.multiply(decimal(1.45 + Math.floorMod(seed, 90) / 100.0, 3))
                .setScale(3, RoundingMode.HALF_UP);
        double riskNoise = (Math.floorMod(seed * 7, 900) / 10000.0) - 0.045;
        BigDecimal riskReductionRate = decimal(
                clamp(RISK_REDUCTION_RATE[combinationIndex] + riskNoise, 0.08, 0.68),
                6
        );
        BigDecimal endRisk = startRisk.multiply(BigDecimal.ONE.subtract(riskReductionRate))
                .setScale(3, RoundingMode.HALF_UP);

        BigDecimal startDisposal = startRisk.multiply(decimal(0.10 + Math.floorMod(seed, 70) / 1000.0, 3))
                .setScale(3, RoundingMode.HALF_UP);
        double disposalNoise = (Math.floorMod(seed * 11, 700) / 10000.0) - 0.035;
        BigDecimal disposalReductionRate = decimal(
                clamp(DISPOSAL_REDUCTION_RATE[combinationIndex] + disposalNoise, 0.12, 0.72),
                6
        );
        BigDecimal endDisposal = startDisposal.multiply(BigDecimal.ONE.subtract(disposalReductionRate))
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal unitCost = decimal(4_500 + Math.floorMod(seed * 29, 31_000), 2);
        BigDecimal savings = startDisposal.subtract(endDisposal).multiply(unitCost)
                .setScale(2, RoundingMode.HALF_UP);

        List<StrategyStatisticsDemoAction> actions = createActions(
                dayKey,
                ordinal,
                actionTypes,
                targetSalesPointId,
                target,
                startDate,
                endDate,
                seed,
                dimensions,
                createdAt
        );

        return new StrategyStatisticsDemoData(
                CASE_ID_BASE + rowKey,
                OPTION_ID_BASE + rowKey,
                SIMULATION_ID_BASE + rowKey,
                SELECTION_ID_BASE + rowKey,
                RESULT_ID_BASE + rowKey,
                skuId,
                dimensions.ownerUserId(),
                dimensions.finalizedSyncRunId(),
                targetSalesPointId,
                "DEMO-STAT-" + endDate.toString().replace("-", "") + "-" + String.format("%02d", ordinal + 1),
                "통계 데모 완료 전략 " + endDate + "-" + (ordinal + 1),
                startDate,
                endDate,
                createdAt,
                completedAt,
                target,
                actual,
                achievementRate,
                startRisk,
                endRisk,
                startDisposal,
                endDisposal,
                unitCost,
                savings,
                actions
        );
    }

    private static List<StrategyStatisticsDemoAction> createActions(
            long dayKey,
            int ordinal,
            List<String> actionTypes,
            long targetSalesPointId,
            BigDecimal target,
            LocalDate startDate,
            LocalDate endDate,
            long seed,
            StrategyStatisticsDemoDimensions dimensions,
            LocalDateTime createdAt
    ) {
        List<StrategyStatisticsDemoAction> actions = new ArrayList<>();
        for (int index = 0; index < actionTypes.size(); index++) {
            String type = actionTypes.get(index);
            boolean movement = "REALLOCATION".equals(type) || "RT_TRANSFER".equals(type);
            Long sourceWarehouseId = movement ? at(dimensions.warehouseIds(), seed + index) : null;
            Long destinationWarehouseId = movement ? at(dimensions.warehouseIds(), seed + index + 1) : null;
            BigDecimal discountRate = "PRICE_DISCOUNT".equals(type)
                    ? decimal(0.10 + Math.floorMod(seed + index, 16) / 100.0, 4)
                    : null;
            BigDecimal strategyPrice = discountRate == null
                    ? null
                    : decimal(12_000 + Math.floorMod(seed * 17, 28_000), 2)
                            .multiply(BigDecimal.ONE.subtract(discountRate))
                            .setScale(2, RoundingMode.HALF_UP);
            actions.add(new StrategyStatisticsDemoAction(
                    ACTION_ID_BASE + dayKey * 100L + ordinal * 10L + index,
                    type,
                    targetSalesPointId,
                    sourceWarehouseId,
                    destinationWarehouseId,
                    target,
                    strategyPrice,
                    discountRate,
                    startDate,
                    endDate,
                    decimal(8_000 + Math.floorMod(seed * 23 + index * 1009L, 65_000), 2),
                    index + 1,
                    createdAt
            ));
        }
        return actions;
    }

    private static long targetSalesPoint(
            List<String> actionTypes,
            long seed,
            StrategyStatisticsDemoDimensions dimensions
    ) {
        boolean preferOnline = actionTypes.contains("CHANNEL_EXPANSION") || Math.floorMod(seed, 4) == 0;
        return preferOnline
                ? at(dimensions.onlineSalesPointIds(), seed)
                : at(dimensions.offlineSalesPointIds(), seed);
    }

    private static int dailyStrategyCount(LocalDate date) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return weekend
                ? 1 + Math.floorMod(date.toEpochDay() * 13L, 2)
                : 2 + Math.floorMod(date.toEpochDay() * 17L, 3);
    }

    private static long at(List<Long> values, long seed) {
        return values.get(Math.floorMod(seed, values.size()));
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

record StrategyStatisticsDemoDimensions(
        List<Long> skuIds,
        List<Long> offlineSalesPointIds,
        List<Long> onlineSalesPointIds,
        List<Long> warehouseIds,
        long ownerUserId,
        long finalizedSyncRunId
) {
}

record StrategyStatisticsDemoData(
        long caseId,
        long optionId,
        long simulationId,
        long selectionId,
        long resultId,
        long skuId,
        long ownerUserId,
        long finalizedSyncRunId,
        long requestedSalesPointId,
        String caseCode,
        String caseName,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        BigDecimal goalTargetValue,
        BigDecimal goalActualValue,
        BigDecimal achievementRate,
        BigDecimal startRiskStockQty,
        BigDecimal endRiskStockQty,
        BigDecimal startExpectedDisposalQty,
        BigDecimal endExpectedDisposalQty,
        BigDecimal startUnitCost,
        BigDecimal estimatedLossSavingsAmount,
        List<StrategyStatisticsDemoAction> actions
) {
}

record StrategyStatisticsDemoAction(
        long actionId,
        String actionType,
        long targetSalesPointId,
        Long sourceWarehouseId,
        Long destinationWarehouseId,
        BigDecimal actionQuantity,
        BigDecimal strategyPrice,
        BigDecimal discountRate,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal estimatedActionCost,
        int actionOrder,
        LocalDateTime createdAt
) {
}
