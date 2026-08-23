package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

final class StrategyStatisticsDemoDataFactory {
    static final long CASE_ID_BASE = 970_000_000_000L;
    static final long OPTION_ID_BASE = 971_000_000_000L;
    static final long SIMULATION_ID_BASE = 972_000_000_000L;
    static final long SELECTION_ID_BASE = 973_000_000_000L;
    static final long RESULT_ID_BASE = 974_000_000_000L;
    static final long ACTION_ID_BASE = 975_000_000_000L;
    static final long INVENTORY_SNAPSHOT_ID_BASE = 976_000_000_000L;
    static final long PERFORMANCE_ID_BASE = 977_000_000_000L;
    static final int STRATEGY_COUNT = 360;

    private static final List<List<String>> ACTION_COMBINATIONS = List.of(
            List.of("PRICE_DISCOUNT"), List.of("REALLOCATION"), List.of("CHANNEL_EXPANSION"),
            List.of("RT_TRANSFER"), List.of("REALLOCATION", "PRICE_DISCOUNT"),
            List.of("RT_TRANSFER", "PRICE_DISCOUNT", "CHANNEL_EXPANSION")
    );
    private static final int[] YEAR_COUNTS = {8, 24, 26, 38, 42, 28, 24, 25, 26, 27, 28, 33, 31};

    List<StrategyStatisticsDemoData> create(LocalDate from, LocalDate to, StrategyStatisticsDemoDimensions dimensions) {
        if (dimensions.salesCandidates().isEmpty() || dimensions.inventoryCandidates().isEmpty()) {
            throw new IllegalStateException("실제 판매와 유효 재고가 연결된 AI 전략 데모 후보가 없습니다.");
        }
        Map<SkuPointKey, StrategyStatisticsDemoSalesCandidate> salesByPoint = new HashMap<>();
        dimensions.salesCandidates().forEach(value -> salesByPoint.put(
                new SkuPointKey(value.skuId(), value.salesPointId()), value));
        Map<Long, List<StrategyStatisticsDemoInventoryCandidate>> inventoryBySku = new HashMap<>();
        dimensions.inventoryCandidates().forEach(value -> inventoryBySku
                .computeIfAbsent(value.skuId(), ignored -> new ArrayList<>()).add(value));

        List<LocalDate> completionDates = completionDates(from, to);
        List<StrategyStatisticsDemoData> result = new ArrayList<>(STRATEGY_COUNT);
        for (int index = 0; index < completionDates.size(); index++) {
            LocalDate end = completionDates.get(index);
            List<String> types = ACTION_COMBINATIONS.get(index % ACTION_COMBINATIONS.size());
            StrategySource source = selectSource(index, end, types, dimensions.salesCandidates(),
                    inventoryBySku, salesByPoint);
            result.add(createOne(index, end, types, source, dimensions, salesByPoint));
        }
        return List.copyOf(result);
    }

    private static StrategySource selectSource(
            int index, LocalDate end, List<String> types,
            List<StrategyStatisticsDemoSalesCandidate> salesCandidates,
            Map<Long, List<StrategyStatisticsDemoInventoryCandidate>> inventoryBySku,
            Map<SkuPointKey, StrategyStatisticsDemoSalesCandidate> salesByPoint
    ) {
        boolean reallocation = types.contains("REALLOCATION");
        boolean transfer = types.contains("RT_TRANSFER");
        for (int offset = 0; offset < salesCandidates.size(); offset++) {
            StrategyStatisticsDemoSalesCandidate sales = salesCandidates.get(
                    Math.floorMod(index * 37 + offset, salesCandidates.size()));
            if (sumSales(sales, end.minusDays(6), end).signum() <= 0) continue;
            List<StrategyStatisticsDemoInventoryCandidate> inventories = inventoryBySku
                    .getOrDefault(sales.skuId(), List.of());
            StrategyStatisticsDemoInventoryCandidate target = inventories.stream()
                    .filter(value -> value.salesPointId() != null && value.salesPointId() == sales.salesPointId())
                    .filter(value -> value.onHandQty().signum() > 0).findFirst().orElse(null);
            if (target == null) continue;
            if (!reallocation && !transfer) return new StrategySource(sales, target, null);
            StrategyStatisticsDemoInventoryCandidate source = inventories.stream()
                    .filter(value -> value.salesPointId() != null)
                    .filter(value -> value.inventoryBalanceId() != target.inventoryBalanceId())
                    .filter(value -> reallocation
                            ? value.warehouseId() == target.warehouseId() && value.salesPointId() != target.salesPointId()
                            : value.warehouseId() != target.warehouseId())
                    .filter(value -> !reallocation || salesByPoint.containsKey(
                            new SkuPointKey(value.skuId(), value.salesPointId())))
                    .findFirst().orElse(null);
            if (source != null) return new StrategySource(sales, target, source);
        }
        throw new IllegalStateException("완료일 " + end + "에 액션 규칙과 실제 판매·재고를 만족하는 후보가 없습니다.");
    }

    private static StrategyStatisticsDemoData createOne(
            int index, LocalDate end, List<String> types, StrategySource source,
            StrategyStatisticsDemoDimensions dimensions,
            Map<SkuPointKey, StrategyStatisticsDemoSalesCandidate> salesByPoint
    ) {
        LocalDate start = end.minusDays(6);
        LocalDateTime created = start.minusDays(2).atTime(LocalTime.of(9, 0));
        LocalDateTime completed = end.atTime(LocalTime.of(4, 30));
        long row = index + 1L;
        NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> daily = relevantSales(
                source, salesByPoint, start, end);
        BigDecimal actual = daily.values().stream().map(StrategyStatisticsDemoSalesDay::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        BigDecimal target = actual.multiply(BigDecimal.valueOf(0.86 + (index % 29) / 100.0))
                .max(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
        BigDecimal achievement = actual.multiply(BigDecimal.valueOf(100))
                .divide(target, 6, RoundingMode.HALF_UP);
        BigDecimal movement = source.sourceInventory() == null ? BigDecimal.ZERO
                : source.targetInventory().onHandQty().min(actual.max(BigDecimal.ONE))
                        .multiply(BigDecimal.valueOf(0.2)).max(BigDecimal.ONE)
                        .setScale(3, RoundingMode.HALF_UP);
        List<StrategyStatisticsDemoInventorySnapshot> snapshots = snapshots(index, source, movement);
        BigDecimal startRisk = snapshots.stream().map(StrategyStatisticsDemoInventorySnapshot::onTotalQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        BigDecimal riskStockReduction = actual.min(startRisk).setScale(3, RoundingMode.HALF_UP);
        BigDecimal endRisk = startRisk.subtract(riskStockReduction).setScale(3, RoundingMode.HALF_UP);
        BigDecimal startDisposal = startRisk.multiply(BigDecimal.valueOf(0.03)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal endDisposal = endRisk.multiply(BigDecimal.valueOf(0.03)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal savings = startDisposal.subtract(endDisposal).multiply(source.targetSales().unitCost())
                .setScale(2, RoundingMode.HALF_UP);
        return new StrategyStatisticsDemoData(
                CASE_ID_BASE + row, OPTION_ID_BASE + row, SIMULATION_ID_BASE + row,
                SELECTION_ID_BASE + row, RESULT_ID_BASE + row, source.targetSales().skuId(),
                dimensions.ownerUserId(), dimensions.finalizedSyncRunId(), source.targetSales().salesPointId(),
                "DEMO-STAT-" + String.format("%04d", row),
                "통계·실행 관제 데모 전략 " + String.format("%04d", row), start, end, created, completed,
                target, actual, achievement, startRisk, endRisk, startDisposal, endDisposal,
                source.targetSales().unitCost(), savings,
                actions(index, types, source, movement, actual, start, end, created), snapshots,
                performance(index, daily, start, end, target, startRisk, movement)
        );
    }

    private static List<StrategyStatisticsDemoAction> actions(
            int index, List<String> types, StrategySource source, BigDecimal movement, BigDecimal actual,
            LocalDate start, LocalDate end, LocalDateTime created
    ) {
        List<StrategyStatisticsDemoAction> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < types.size(); ordinal++) {
            String type = types.get(ordinal);
            boolean moves = "REALLOCATION".equals(type) || "RT_TRANSFER".equals(type);
            BigDecimal discount = "PRICE_DISCOUNT".equals(type)
                    ? BigDecimal.valueOf(0.10 + (index % 16) / 100.0).setScale(4, RoundingMode.HALF_UP) : null;
            BigDecimal price = discount == null ? null : source.targetSales().sellingPrice()
                    .multiply(BigDecimal.ONE.subtract(discount)).setScale(2, RoundingMode.HALF_UP);
            result.add(new StrategyStatisticsDemoAction(
                    ACTION_ID_BASE + index * 10L + ordinal + 1, type,
                    "REALLOCATION".equals(type) ? source.sourceInventory().salesPointId() : null,
                    source.targetSales().salesPointId(),
                    moves ? source.sourceInventory().warehouseId() : null,
                    moves ? source.targetInventory().warehouseId() : null,
                    moves ? movement : actual, price, discount, start, end,
                    BigDecimal.valueOf(8_000L + (index * 997L + ordinal * 431L) % 65_000L).setScale(2),
                    ordinal + 1, created));
        }
        return List.copyOf(result);
    }

    private static List<StrategyStatisticsDemoInventorySnapshot> snapshots(
            int index, StrategySource source, BigDecimal movement
    ) {
        List<StrategyStatisticsDemoInventorySnapshot> result = new ArrayList<>();
        if (source.sourceInventory() == null) {
            result.add(snapshot(index, 0, source.targetInventory(), source.targetInventory().onHandQty(),
                    source.targetInventory().totalQty()));
        } else {
            result.add(snapshot(index, 0, source.sourceInventory(),
                    source.sourceInventory().onHandQty().add(movement),
                    source.sourceInventory().totalQty().add(movement)));
            result.add(snapshot(index, 1, source.targetInventory(),
                    source.targetInventory().onHandQty().subtract(movement).max(BigDecimal.ZERO),
                    source.targetInventory().totalQty().subtract(movement).max(BigDecimal.ZERO)));
        }
        return List.copyOf(result);
    }

    private static StrategyStatisticsDemoInventorySnapshot snapshot(
            int index, int ordinal, StrategyStatisticsDemoInventoryCandidate value,
            BigDecimal beforeHand, BigDecimal beforeTotal
    ) {
        return new StrategyStatisticsDemoInventorySnapshot(
                INVENTORY_SNAPSHOT_ID_BASE + index * 10L + ordinal + 1,
                value.inventoryBalanceId(), value.skuId(), value.lotId(), value.salesPointId(), value.warehouseId(),
                beforeTotal.setScale(3, RoundingMode.HALF_UP), beforeHand.setScale(3, RoundingMode.HALF_UP),
                value.totalQty(), value.onHandQty(), value.safetyStockQty(), value.dailySalesVelocity(),
                value.forecastQty(), value.expiryDate());
    }

    private static List<StrategyStatisticsDemoPerformance> performance(
            int index, NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> daily,
            LocalDate start, LocalDate end, BigDecimal target, BigDecimal startStock, BigDecimal movement
    ) {
        List<StrategyStatisticsDemoPerformance> result = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        int ordinal = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            StrategyStatisticsDemoSalesDay sales = daily.getOrDefault(date,
                    new StrategyStatisticsDemoSalesDay(date, BigDecimal.ZERO, BigDecimal.ZERO));
            cumulative = cumulative.add(sales.quantity());
            result.add(new StrategyStatisticsDemoPerformance(
                    PERFORMANCE_ID_BASE + index * 10L + ordinal + 1, date, sales.quantity(), sales.revenue(),
                    sales.revenue().multiply(BigDecimal.valueOf(0.45)).setScale(2, RoundingMode.HALF_UP),
                    startStock.subtract(cumulative).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP),
                    ordinal == 0 ? movement : BigDecimal.ZERO, BigDecimal.ZERO,
                    cumulative.multiply(BigDecimal.valueOf(100)).divide(target, 6, RoundingMode.HALF_UP)));
            ordinal++;
        }
        return List.copyOf(result);
    }

    private static NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> relevantSales(
            StrategySource source, Map<SkuPointKey, StrategyStatisticsDemoSalesCandidate> salesByPoint,
            LocalDate from, LocalDate to
    ) {
        NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> result = new TreeMap<>();
        mergeSales(result, source.targetSales(), from, to);
        if (source.sourceInventory() != null
                && source.sourceInventory().warehouseId() == source.targetInventory().warehouseId()
                && source.sourceInventory().salesPointId() != source.targetSales().salesPointId()) {
            StrategyStatisticsDemoSalesCandidate sourceSales = salesByPoint.get(new SkuPointKey(
                    source.targetSales().skuId(), source.sourceInventory().salesPointId()));
            if (sourceSales != null) mergeSales(result, sourceSales, from, to);
        }
        return result;
    }

    private static void mergeSales(
            NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> target,
            StrategyStatisticsDemoSalesCandidate source, LocalDate from, LocalDate to
    ) {
        source.dailySales().subMap(from, true, to, true).values().forEach(value -> target.merge(value.date(), value,
                (left, right) -> new StrategyStatisticsDemoSalesDay(left.date(),
                        left.quantity().add(right.quantity()), left.revenue().add(right.revenue()))));
    }

    private static BigDecimal sumSales(StrategyStatisticsDemoSalesCandidate value, LocalDate from, LocalDate to) {
        return value.dailySales().subMap(from, true, to, true).values().stream()
                .map(StrategyStatisticsDemoSalesDay::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<LocalDate> completionDates(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            months.add(month);
        }
        int[] quotas = months.size() == YEAR_COUNTS.length && days >= 365 ? YEAR_COUNTS
                : proportionalQuotas(months, from, to);
        List<LocalDate> result = new ArrayList<>(STRATEGY_COUNT);
        for (int monthIndex = 0; monthIndex < months.size(); monthIndex++) {
            LocalDate first = months.get(monthIndex).atDay(1).isBefore(from) ? from : months.get(monthIndex).atDay(1);
            if (monthIndex == 0 && first.isBefore(from.plusDays(6))) first = from.plusDays(6);
            LocalDate last = months.get(monthIndex).atEndOfMonth().isAfter(to) ? to : months.get(monthIndex).atEndOfMonth();
            int availableDays = Math.toIntExact(ChronoUnit.DAYS.between(first, last) + 1);
            for (int ordinal = 0; ordinal < quotas[monthIndex]; ordinal++) {
                int offset = Math.min(availableDays - 1,
                        (int) Math.floor((ordinal + 0.5) * availableDays / quotas[monthIndex]));
                result.add(first.plusDays(offset));
            }
        }
        result.sort(Comparator.naturalOrder());
        if (result.size() != STRATEGY_COUNT) {
            throw new IllegalStateException("AI 전략 데모 완료일 생성 건수가 360건이 아닙니다: " + result.size());
        }
        return result;
    }

    private static int[] proportionalQuotas(List<YearMonth> months, LocalDate from, LocalDate to) {
        int[] result = new int[months.size()];
        int assigned = 0;
        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        for (int index = 0; index < months.size(); index++) {
            LocalDate first = months.get(index).atDay(1).isBefore(from) ? from : months.get(index).atDay(1);
            LocalDate last = months.get(index).atEndOfMonth().isAfter(to) ? to : months.get(index).atEndOfMonth();
            result[index] = (int) Math.floor((double) STRATEGY_COUNT
                    * (ChronoUnit.DAYS.between(first, last) + 1) / totalDays);
            assigned += result[index];
        }
        for (int index = 0; assigned < STRATEGY_COUNT; index = (index + 1) % result.length) {
            result[index]++;
            assigned++;
        }
        return result;
    }

    private record SkuPointKey(long skuId, long salesPointId) {}
    private record StrategySource(
            StrategyStatisticsDemoSalesCandidate targetSales,
            StrategyStatisticsDemoInventoryCandidate targetInventory,
            StrategyStatisticsDemoInventoryCandidate sourceInventory
    ) {}
}

record StrategyStatisticsDemoDimensions(
        List<StrategyStatisticsDemoSalesCandidate> salesCandidates,
        List<StrategyStatisticsDemoInventoryCandidate> inventoryCandidates,
        long ownerUserId,
        long finalizedSyncRunId
) {}

record StrategyStatisticsDemoSalesCandidate(
        long skuId, long salesPointId, NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> dailySales,
        BigDecimal unitCost, BigDecimal sellingPrice
) {
    StrategyStatisticsDemoSalesCandidate { dailySales = new TreeMap<>(dailySales); }
}

record StrategyStatisticsDemoSalesDay(LocalDate date, BigDecimal quantity, BigDecimal revenue) {}

record StrategyStatisticsDemoInventoryCandidate(
        long inventoryBalanceId, long skuId, long lotId, Long salesPointId, long warehouseId,
        BigDecimal totalQty, BigDecimal onHandQty, BigDecimal safetyStockQty,
        BigDecimal dailySalesVelocity, BigDecimal forecastQty, LocalDate expiryDate
) {}

record StrategyStatisticsDemoData(
        long caseId, long optionId, long simulationId, long selectionId, long resultId,
        long skuId, long ownerUserId, long finalizedSyncRunId, long requestedSalesPointId,
        String caseCode, String caseName, LocalDate startDate, LocalDate endDate,
        LocalDateTime createdAt, LocalDateTime completedAt,
        BigDecimal goalTargetValue, BigDecimal goalActualValue, BigDecimal achievementRate,
        BigDecimal startRiskStockQty, BigDecimal endRiskStockQty,
        BigDecimal startExpectedDisposalQty, BigDecimal endExpectedDisposalQty,
        BigDecimal startUnitCost, BigDecimal estimatedLossSavingsAmount,
        List<StrategyStatisticsDemoAction> actions,
        List<StrategyStatisticsDemoInventorySnapshot> inventorySnapshots,
        List<StrategyStatisticsDemoPerformance> performance
) {}

record StrategyStatisticsDemoAction(
        long actionId, String actionType, Long sourceSalesPointId, long targetSalesPointId,
        Long sourceWarehouseId, Long destinationWarehouseId, BigDecimal actionQuantity,
        BigDecimal strategyPrice, BigDecimal discountRate, LocalDate startDate, LocalDate endDate,
        BigDecimal estimatedActionCost, int actionOrder, LocalDateTime createdAt
) {}

record StrategyStatisticsDemoInventorySnapshot(
        long inventorySnapshotId, long inventoryBalanceId, long skuId, long lotId,
        Long salesPointId, long warehouseId, BigDecimal onTotalQty, BigDecimal onHandQty,
        BigDecimal currentTotalQty, BigDecimal currentOnHandQty, BigDecimal safetyStockQty,
        BigDecimal dailySalesVelocity, BigDecimal forecastQty, LocalDate expiryDate
) {}

record StrategyStatisticsDemoPerformance(
        long performanceId, LocalDate performanceDate, BigDecimal actualSalesQty,
        BigDecimal actualRevenue, BigDecimal actualContributionMargin,
        BigDecimal actualRemainingQty, BigDecimal movedQuantity, BigDecimal disposedQuantity,
        BigDecimal achievementRate
) {}
