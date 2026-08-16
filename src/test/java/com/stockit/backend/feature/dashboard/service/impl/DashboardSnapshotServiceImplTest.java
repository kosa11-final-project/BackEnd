package com.stockit.backend.feature.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.dashboard.dto.DashboardSnapshotPayload;
import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.mapper.DashboardSnapshotMapper;
import com.stockit.backend.feature.dashboard.service.DashboardService;

@ExtendWith(MockitoExtension.class)
class DashboardSnapshotServiceImplTest {

    private static final Long SYNC_JOB_ID = 101L;
    private static final Long SNAPSHOT_ID = 31L;
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 16);
    private static final Instant CALCULATED_AT = Instant.parse("2026-08-16T01:05:00Z");
    @Mock
    private DashboardService dashboardService;

    @Mock
    private DashboardSnapshotMapper snapshotMapper;

    private ObjectMapper objectMapper;
    private DashboardSnapshotServiceImpl snapshotService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        snapshotService = new DashboardSnapshotServiceImpl(
                dashboardService,
                snapshotMapper,
                objectMapper
        );
    }

    @Test
    void storesLiveDashboardAsSingleJsonSnapshot() throws Exception {
        when(snapshotMapper.selectSnapshotIdBySyncJobId(SYNC_JOB_ID)).thenReturn(null);
        when(snapshotMapper.selectNextSnapshotId()).thenReturn(SNAPSHOT_ID);
        when(dashboardService.getLiveDashboard(AS_OF_DATE)).thenReturn(dashboard());
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        Long result = snapshotService.createSnapshot(SYNC_JOB_ID, AS_OF_DATE);

        assertThat(result).isEqualTo(SNAPSHOT_ID);
        verify(snapshotMapper).insertSnapshot(
                eq(SNAPSHOT_ID),
                eq(SYNC_JOB_ID),
                eq(1),
                payloadCaptor.capture()
        );
        DashboardSnapshotPayload payload = objectMapper.readValue(
                payloadCaptor.getValue(),
                DashboardSnapshotPayload.class
        );
        assertThat(payload.summary().totalAvailableStock()).isEqualByComparingTo("4062");
        assertThat(payload.warehouses()).isEmpty();
    }

    @Test
    void returnsExistingSnapshotForSameSyncJob() {
        when(snapshotMapper.selectSnapshotIdBySyncJobId(SYNC_JOB_ID)).thenReturn(SNAPSHOT_ID);

        Long result = snapshotService.createSnapshot(SYNC_JOB_ID, AS_OF_DATE);

        assertThat(result).isEqualTo(SNAPSHOT_ID);
        verify(snapshotMapper, never()).selectNextSnapshotId();
        verifyNoInteractions(dashboardService);
    }

    private static DashboardResponse dashboard() {
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                new BigDecimal("4062"),
                5,
                7,
                12,
                9,
                new BigDecimal("519")
        );
        return new DashboardResponse(
                summary,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                CALCULATED_AT
        );
    }
}
