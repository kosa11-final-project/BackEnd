package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.StatisticsSnapshotPayload;
import com.stockit.backend.feature.statistics.dto.response.InventoryFinancialStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDataQualityResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.dto.response.RiskGradeStatisticsResponse;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.vo.StatisticsSnapshotVO;

@ExtendWith(MockitoExtension.class)
class InventoryStatisticsServiceImplTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private StatisticsSnapshotMapper snapshotMapper;

    private ObjectMapper objectMapper;
    private InventoryStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new InventoryStatisticsServiceImpl(snapshotMapper, objectMapper);
    }

    @Test
    void composesLatestScopeSummariesLocationsAndSelectedTrend() throws Exception {
        StatisticsSnapshotVO national = snapshot("NATIONAL", "ALL", "전국", summary(100, 1000));
        StatisticsSnapshotVO warehouses = snapshot("WAREHOUSE", "ALL", "전체 물류센터", summary(90, 800));
        StatisticsSnapshotVO seongnam = snapshot("WAREHOUSE", "WH_SEONGNAM", "성남센터", summary(40, 300));
        seongnam.setWarehouseId(1L);
        seongnam.setRegionCode("GYEONGGI");
        when(snapshotMapper.selectLatestSnapshots(AS_OF_DATE))
                .thenReturn(List.of(national, warehouses, seongnam));
        when(snapshotMapper.selectTrendSnapshots(
                "WAREHOUSE", "WH_SEONGNAM", AS_OF_DATE.minusDays(6), AS_OF_DATE
        )).thenReturn(List.of(seongnam));

        InventoryStatisticsResponse response = service.getInventoryStatistics(
                AS_OF_DATE.minusDays(6),
                AS_OF_DATE,
                StatisticsScopeType.WAREHOUSE,
                "WH_SEONGNAM"
        );

        assertThat(response.scopeSummaries()).containsKeys("NATIONAL", "WAREHOUSE");
        assertThat(response.locations()).singleElement()
                .satisfies(location -> {
                    assertThat(location.code()).isEqualTo("WH_SEONGNAM");
                    assertThat(location.region()).isEqualTo("GYEONGGI");
                    assertThat(location.criticalStockRatio()).isEqualByComparingTo("10.0000");
                });
        assertThat(response.dailyTrend()).singleElement()
                .satisfies(point -> {
                    assertThat(point.totalStockQty()).isEqualByComparingTo("300");
                    assertThat(point.criticalStockQty()).isEqualByComparingTo("30");
                    assertThat(point.warningStockQty()).isZero();
                    assertThat(point.riskStockQty()).isEqualByComparingTo("30");
                    assertThat(point.riskStockRatio()).isEqualByComparingTo("10.0000");
                    assertThat(point.riskSkuCount()).isEqualTo(3);
                });
    }

    private StatisticsSnapshotVO snapshot(
            String scopeType,
            String scopeCode,
            String scopeName,
            InventoryStatisticsSummaryResponse summary
    ) throws Exception {
        StatisticsSnapshotVO value = new StatisticsSnapshotVO();
        value.setStatisticsSnapshotId(1L);
        value.setSyncJobId(100L);
        value.setAsOfDate(AS_OF_DATE);
        value.setScopeType(scopeType);
        value.setScopeCode(scopeCode);
        value.setScopeName(scopeName);
        value.setPayloadVersion(1);
        value.setPayloadJson(objectMapper.writeValueAsString(new StatisticsSnapshotPayload(summary)));
        value.setCreatedAt(LocalDateTime.of(2026, 8, 17, 3, 7));
        return value;
    }

    private static InventoryStatisticsSummaryResponse summary(long skuCount, long stockQty) {
        BigDecimal total = BigDecimal.valueOf(stockQty);
        BigDecimal critical = BigDecimal.valueOf(30);
        return new InventoryStatisticsSummaryResponse(
                skuCount,
                total,
                total.subtract(BigDecimal.TEN),
                3,
                critical,
                2,
                BigDecimal.ONE,
                List.of(
                        new RiskGradeStatisticsResponse("CRITICAL", 3, critical),
                        new RiskGradeStatisticsResponse("WARNING", 0, BigDecimal.ZERO),
                        new RiskGradeStatisticsResponse("NORMAL", 0, BigDecimal.ZERO),
                        new RiskGradeStatisticsResponse("GOOD", skuCount - 3, total.subtract(critical)),
                        new RiskGradeStatisticsResponse("UNASSESSED", 0, BigDecimal.ZERO)
                ),
                new InventoryStatisticsDataQualityResponse(0, BigDecimal.ZERO, 0, BigDecimal.ZERO),
                new InventoryFinancialStatisticsResponse(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO
                )
        );
    }
}
