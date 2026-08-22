package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.dto.StatisticsSnapshotPayload;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDemoBackfillResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.mapper.InventoryStatisticsAggregationMapper;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.service.InventoryStatisticsDemoBackfillService;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsDailySalesVO;

@Service
public class InventoryStatisticsDemoBackfillServiceImpl implements InventoryStatisticsDemoBackfillService {
    static final long DEMO_SYNC_JOB_BASE = 960_000_000_000L;
    private static final int PAYLOAD_VERSION = 1;
    private static final int MAX_DAYS = 366;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final InventoryStatisticsAggregationMapper aggregationMapper;
    private final StatisticsSnapshotMapper snapshotMapper;
    private final InventoryStatisticsDemoTrendSimulator simulator;
    private final ObjectMapper objectMapper;

    public InventoryStatisticsDemoBackfillServiceImpl(
            InventoryStatisticsAggregationMapper aggregationMapper,
            StatisticsSnapshotMapper snapshotMapper,
            InventoryStatisticsDemoTrendSimulator simulator,
            ObjectMapper objectMapper
    ) {
        this.aggregationMapper = aggregationMapper;
        this.snapshotMapper = snapshotMapper;
        this.simulator = simulator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public InventoryStatisticsDemoBackfillResponse backfill(LocalDate fromDate, LocalDate toDate) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now(BUSINESS_ZONE) : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusMonths(6) : fromDate;
        int dateCount = validateRange(resolvedFrom, resolvedTo);

        List<InventoryStatisticsAggregateVO> currentAggregates = aggregationMapper
                .selectScopeAggregates(resolvedTo);
        if (currentAggregates.isEmpty()) {
            throw new IllegalStateException("현재 재고 집계 결과가 없어 데모 이력을 생성할 수 없습니다.");
        }

        Map<LocalDate, Double> salesActivity = salesActivity(
                aggregationMapper.selectNationalDailySales(resolvedFrom, resolvedTo),
                resolvedFrom,
                resolvedTo
        );

        int createdDates = 0;
        int skippedDates = 0;
        int createdSnapshots = 0;
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            long syncJobId = demoSyncJobId(date);
            if (!snapshotMapper.selectSnapshotIdsBySyncJobId(syncJobId).isEmpty()) {
                skippedDates++;
                continue;
            }

            for (InventoryStatisticsAggregateVO aggregate : currentAggregates) {
                InventoryStatisticsSummaryResponse current = InventoryStatisticsSummaryResponse.from(aggregate);
                InventoryStatisticsSummaryResponse historical = simulator.simulate(
                        current,
                        date,
                        resolvedFrom,
                        resolvedTo,
                        salesActivity.getOrDefault(date, 1.0),
                        aggregate.getScopeType() + ":" + aggregate.getScopeCode()
                );
                snapshotMapper.insertSnapshot(
                        snapshotMapper.selectNextSnapshotId(),
                        syncJobId,
                        date,
                        aggregate,
                        PAYLOAD_VERSION,
                        serialize(new StatisticsSnapshotPayload(historical))
                );
                createdSnapshots++;
            }
            createdDates++;
        }

        return new InventoryStatisticsDemoBackfillResponse(
                resolvedFrom,
                resolvedTo,
                dateCount,
                createdDates,
                skippedDates,
                createdSnapshots
        );
    }

    static long demoSyncJobId(LocalDate date) {
        return DEMO_SYNC_JOB_BASE + date.toEpochDay();
    }

    private static int validateRange(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days <= 0 || days > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return Math.toIntExact(days);
    }

    private static Map<LocalDate, Double> salesActivity(
            List<InventoryStatisticsDailySalesVO> rows,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Map<LocalDate, BigDecimal> raw = new HashMap<>();
        rows.forEach(row -> raw.put(row.getSalesDate(), zero(row.getSalesQty())));

        Map<LocalDate, BigDecimal> smoothed = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            BigDecimal sevenDayTotal = BigDecimal.ZERO;
            for (int offset = -3; offset <= 3; offset++) {
                sevenDayTotal = sevenDayTotal.add(raw.getOrDefault(date.plusDays(offset), BigDecimal.ZERO));
            }
            BigDecimal average = sevenDayTotal.divide(BigDecimal.valueOf(7), 6, java.math.RoundingMode.HALF_UP);
            smoothed.put(date, average);
            if (average.signum() > 0) {
                total = total.add(average);
                count++;
            }
        }
        double mean = count == 0 ? 0 : total.doubleValue() / count;

        Map<LocalDate, Double> result = new HashMap<>();
        smoothed.forEach((date, value) -> result.put(
                date,
                mean == 0 ? 1.0 : Math.max(0.6, Math.min(1.4, value.doubleValue() / mean))
        ));
        return result;
    }

    private String serialize(StatisticsSnapshotPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("재고 통계 데모 JSON 생성에 실패했습니다.", exception);
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
