package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.statistics.dto.StatisticsSnapshotPayload;
import com.stockit.backend.feature.statistics.mapper.InventoryStatisticsAggregationMapper;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;

@ExtendWith(MockitoExtension.class)
class StatisticsSnapshotServiceImplTest {

    private static final Long SYNC_JOB_ID = 101L;
    private static final Long SNAPSHOT_ID = 31L;
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private InventoryStatisticsAggregationMapper aggregationMapper;
    @Mock
    private StatisticsSnapshotMapper snapshotMapper;

    private ObjectMapper objectMapper;
    private StatisticsSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new StatisticsSnapshotServiceImpl(aggregationMapper, snapshotMapper, objectMapper);
    }

    @Test
    void createsScopeSnapshotsAsVersionedJson() throws Exception {
        InventoryStatisticsAggregateVO aggregate = aggregate();
        when(snapshotMapper.selectSnapshotIdsBySyncJobId(SYNC_JOB_ID)).thenReturn(List.of());
        when(aggregationMapper.selectScopeAggregates(AS_OF_DATE)).thenReturn(List.of(aggregate));
        when(snapshotMapper.selectNextSnapshotId()).thenReturn(SNAPSHOT_ID);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        List<Long> result = service.createInventorySnapshots(SYNC_JOB_ID, AS_OF_DATE);

        assertThat(result).containsExactly(SNAPSHOT_ID);
        verify(snapshotMapper).insertSnapshot(
                eq(SNAPSHOT_ID),
                eq(SYNC_JOB_ID),
                eq(AS_OF_DATE),
                eq(aggregate),
                eq(1),
                payloadCaptor.capture()
        );
        StatisticsSnapshotPayload payload = objectMapper.readValue(
                payloadCaptor.getValue(),
                StatisticsSnapshotPayload.class
        );
        assertThat(payload.inventory().totalStockQty()).isEqualByComparingTo("3208895");
        assertThat(payload.inventory().riskDistribution()).hasSize(5);
    }

    @Test
    void returnsExistingSnapshotsForSameSyncJob() {
        when(snapshotMapper.selectSnapshotIdsBySyncJobId(SYNC_JOB_ID)).thenReturn(List.of(10L, 11L));

        List<Long> result = service.createInventorySnapshots(SYNC_JOB_ID, AS_OF_DATE);

        assertThat(result).containsExactly(10L, 11L);
        verify(snapshotMapper, never()).selectNextSnapshotId();
        verifyNoInteractions(aggregationMapper);
    }

    private static InventoryStatisticsAggregateVO aggregate() {
        InventoryStatisticsAggregateVO value = new InventoryStatisticsAggregateVO();
        value.setScopeType("NATIONAL");
        value.setScopeCode("ALL");
        value.setScopeName("전국");
        value.setTotalSkuCount(9277);
        value.setTotalStockQty(new BigDecimal("3208895"));
        value.setAvailableStockQty(new BigDecimal("3070127"));
        value.setCriticalSkuCount(1297);
        value.setWarningSkuCount(5524);
        value.setNormalSkuCount(2100);
        value.setGoodSkuCount(300);
        value.setUnassessedDistributionSkuCount(56);
        value.setCriticalStockQty(new BigDecimal("182430"));
        value.setWarningStockQty(new BigDecimal("924510"));
        value.setNormalStockQty(new BigDecimal("1742010"));
        value.setGoodStockQty(new BigDecimal("311812"));
        value.setUnassessedDistributionStockQty(new BigDecimal("48133"));
        value.setShortageSkuCount(6821);
        value.setExpectedDisposalQty30d(new BigDecimal("519"));
        value.setTotalInventoryCostAmount(new BigDecimal("4821500000"));
        value.setCriticalInventoryCostAmount(new BigDecimal("393400000"));
        value.setExpectedDisposalLossAmount30d(new BigDecimal("12750000"));
        value.setMissingCostSkuCount(12);
        value.setMissingCostStockQty(new BigDecimal("4280"));
        value.setUnassessedSkuCount(56);
        value.setUnassessedStockQty(new BigDecimal("48133"));
        value.setMissingForecastSkuCount(83);
        value.setMissingForecastStockQty(new BigDecimal("17240"));
        return value;
    }
}
