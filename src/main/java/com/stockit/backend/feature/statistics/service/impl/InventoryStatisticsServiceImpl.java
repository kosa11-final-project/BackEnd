package com.stockit.backend.feature.statistics.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.StatisticsSnapshotPayload;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsLocationResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsTrendPointResponse;
import com.stockit.backend.feature.statistics.dto.response.RiskGradeStatisticsResponse;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.service.InventoryStatisticsService;
import com.stockit.backend.feature.statistics.vo.StatisticsSnapshotVO;

@Service
@Transactional(readOnly = true)
public class InventoryStatisticsServiceImpl implements InventoryStatisticsService {

    private static final int SUPPORTED_PAYLOAD_VERSION = 1;
    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int MAX_TREND_DAYS = 366;
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final StatisticsSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public InventoryStatisticsServiceImpl(
            StatisticsSnapshotMapper snapshotMapper,
            ObjectMapper objectMapper
    ) {
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public InventoryStatisticsResponse getInventoryStatistics(
            LocalDate fromDate,
            LocalDate toDate,
            StatisticsScopeType trendScopeType,
            String trendScopeCode
    ) {
        List<StatisticsSnapshotVO> latestSnapshots = snapshotMapper.selectLatestSnapshots(toDate);
        if (latestSnapshots.isEmpty()) {
            throw new AppException(ErrorCode.STATISTICS_SNAPSHOT_NOT_FOUND);
        }

        StatisticsSnapshotVO national = latestSnapshots.stream()
                .filter(snapshot -> StatisticsScopeType.NATIONAL.name().equals(snapshot.getScopeType()))
                .filter(snapshot -> "ALL".equals(snapshot.getScopeCode()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.STATISTICS_SNAPSHOT_NOT_FOUND));

        LocalDate resolvedToDate = toDate == null ? national.getAsOfDate() : toDate;
        LocalDate resolvedFromDate = fromDate == null
                ? resolvedToDate.minusDays(DEFAULT_TREND_DAYS - 1L)
                : fromDate;
        validateDateRange(resolvedFromDate, resolvedToDate);

        StatisticsScopeType resolvedScopeType = trendScopeType == null
                ? StatisticsScopeType.NATIONAL
                : trendScopeType;
        String resolvedScopeCode = resolveScopeCode(resolvedScopeType, trendScopeCode);

        Map<String, InventoryStatisticsSummaryResponse> scopeSummaries = new LinkedHashMap<>();
        List<InventoryStatisticsLocationResponse> locations = new ArrayList<>();

        for (StatisticsSnapshotVO snapshot : latestSnapshots) {
            InventoryStatisticsSummaryResponse summary = deserialize(snapshot).inventory();
            if (isScopeSummary(snapshot)) {
                scopeSummaries.put(snapshot.getScopeType(), summary);
            }
            if (isLocation(snapshot)) {
                locations.add(InventoryStatisticsLocationResponse.from(
                        snapshot.getScopeType(),
                        snapshot.getScopeCode(),
                        snapshot.getScopeName(),
                        resolveRegion(snapshot),
                        summary
                ));
            }
        }

        List<InventoryStatisticsTrendPointResponse> trend = snapshotMapper.selectTrendSnapshots(
                        resolvedScopeType.name(),
                        resolvedScopeCode,
                        resolvedFromDate,
                        resolvedToDate
                ).stream()
                .map(this::toTrendPoint)
                .toList();

        Instant calculatedAt = national.getCreatedAt().atZone(KOREA_ZONE_ID).toInstant();
        return new InventoryStatisticsResponse(
                national.getAsOfDate(),
                calculatedAt,
                true,
                resolvedScopeType.name(),
                resolvedScopeCode,
                scopeSummaries,
                locations,
                trend
        );
    }

    private static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days <= 0 || days > MAX_TREND_DAYS) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private static String resolveScopeCode(StatisticsScopeType scopeType, String scopeCode) {
        if (scopeCode != null && !scopeCode.isBlank()) {
            return scopeCode.trim();
        }
        return scopeType == StatisticsScopeType.UNASSIGNED ? "UNASSIGNED" : "ALL";
    }

    private static boolean isScopeSummary(StatisticsSnapshotVO snapshot) {
        return "ALL".equals(snapshot.getScopeCode())
                || StatisticsScopeType.UNASSIGNED.name().equals(snapshot.getScopeType());
    }

    private static boolean isLocation(StatisticsSnapshotVO snapshot) {
        return !StatisticsScopeType.NATIONAL.name().equals(snapshot.getScopeType())
                && !"ALL".equals(snapshot.getScopeCode());
    }

    private static String resolveRegion(StatisticsSnapshotVO snapshot) {
        if (StatisticsScopeType.UNASSIGNED.name().equals(snapshot.getScopeType())) {
            return "전국 물류센터";
        }
        if (StatisticsScopeType.ONLINE_STORE.name().equals(snapshot.getScopeType())) {
            return "온라인";
        }
        return snapshot.getRegionCode() == null ? "미분류" : snapshot.getRegionCode();
    }

    private InventoryStatisticsTrendPointResponse toTrendPoint(StatisticsSnapshotVO snapshot) {
        InventoryStatisticsSummaryResponse summary = deserialize(snapshot).inventory();
        RiskGradeStatisticsResponse warning = riskGrade(summary, "WARNING");
        BigDecimal criticalStockQty = nullToZero(summary.criticalStockQty());
        BigDecimal warningStockQty = nullToZero(warning.stockQty());
        BigDecimal riskStockQty = criticalStockQty.add(warningStockQty);
        BigDecimal totalStockQty = nullToZero(summary.totalStockQty());
        BigDecimal riskStockRatio = totalStockQty.signum() == 0
                ? BigDecimal.ZERO
                : riskStockQty.multiply(BigDecimal.valueOf(100))
                        .divide(totalStockQty, 4, RoundingMode.HALF_UP);
        return new InventoryStatisticsTrendPointResponse(
                snapshot.getAsOfDate(),
                totalStockQty,
                summary.criticalSkuCount(),
                warning.skuCount(),
                summary.criticalSkuCount() + warning.skuCount(),
                criticalStockQty,
                warningStockQty,
                riskStockQty,
                riskStockRatio,
                nullToZero(summary.expectedDisposalQty30d()),
                summary.financialSummary() == null
                        ? BigDecimal.ZERO
                        : nullToZero(summary.financialSummary().expectedDisposalLossAmount30d()),
                summary.shortageSkuCount()
        );
    }

    private static RiskGradeStatisticsResponse riskGrade(
            InventoryStatisticsSummaryResponse summary,
            String grade
    ) {
        if (summary.riskDistribution() == null) {
            return new RiskGradeStatisticsResponse(grade, 0, BigDecimal.ZERO);
        }
        return summary.riskDistribution().stream()
                .filter(item -> grade.equals(item.riskGrade()))
                .findFirst()
                .orElseGet(() -> new RiskGradeStatisticsResponse(grade, 0, BigDecimal.ZERO));
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private StatisticsSnapshotPayload deserialize(StatisticsSnapshotVO snapshot) {
        if (snapshot.getPayloadVersion() != SUPPORTED_PAYLOAD_VERSION) {
            throw new IllegalStateException("지원하지 않는 통계 스냅샷 버전입니다: "
                    + snapshot.getPayloadVersion());
        }
        try {
            return objectMapper.readValue(snapshot.getPayloadJson(), StatisticsSnapshotPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("통계 스냅샷 JSON 조회에 실패했습니다.", exception);
        }
    }
}
