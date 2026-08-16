package com.stockit.backend.feature.dashboard.service.impl;

import java.time.LocalDate;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.dashboard.dto.DashboardSnapshotPayload;
import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.mapper.DashboardSnapshotMapper;
import com.stockit.backend.feature.dashboard.service.DashboardService;
import com.stockit.backend.feature.dashboard.service.DashboardSnapshotService;

@Service
public class DashboardSnapshotServiceImpl implements DashboardSnapshotService {

    private static final int PAYLOAD_VERSION = 1;

    private final DashboardService dashboardService;
    private final DashboardSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public DashboardSnapshotServiceImpl(
            DashboardService dashboardService,
            DashboardSnapshotMapper snapshotMapper,
            ObjectMapper objectMapper
    ) {
        this.dashboardService = dashboardService;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Long createSnapshot(Long syncJobId, LocalDate asOfDate) {
        Objects.requireNonNull(syncJobId, "syncJobId must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        Long existingSnapshotId = snapshotMapper.selectSnapshotIdBySyncJobId(syncJobId);
        if (existingSnapshotId != null) {
            return existingSnapshotId;
        }

        DashboardResponse liveDashboard = dashboardService.getLiveDashboard(asOfDate);
        String payloadJson = serialize(DashboardSnapshotPayload.from(liveDashboard));
        Long snapshotId = snapshotMapper.selectNextSnapshotId();

        snapshotMapper.insertSnapshot(
                snapshotId,
                syncJobId,
                PAYLOAD_VERSION,
                payloadJson
        );
        return snapshotId;
    }

    private String serialize(DashboardSnapshotPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("대시보드 스냅샷 JSON 생성에 실패했습니다.", exception);
        }
    }
}
