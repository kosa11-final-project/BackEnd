package com.stockit.backend.feature.statistics.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.statistics.dto.StatisticsSnapshotPayload;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.mapper.InventoryStatisticsAggregationMapper;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;

@Service
public class StatisticsSnapshotServiceImpl implements StatisticsSnapshotService {

    private static final int PAYLOAD_VERSION = 1;

    private final InventoryStatisticsAggregationMapper aggregationMapper;
    private final StatisticsSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public StatisticsSnapshotServiceImpl(
            InventoryStatisticsAggregationMapper aggregationMapper,
            StatisticsSnapshotMapper snapshotMapper,
            ObjectMapper objectMapper
    ) {
        this.aggregationMapper = aggregationMapper;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<Long> createInventorySnapshots(Long syncJobId, LocalDate asOfDate) {
        Objects.requireNonNull(syncJobId, "syncJobId must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        List<Long> existingIds = snapshotMapper.selectSnapshotIdsBySyncJobId(syncJobId);
        if (!existingIds.isEmpty()) {
            return existingIds;
        }

        List<InventoryStatisticsAggregateVO> aggregates = aggregationMapper.selectScopeAggregates(asOfDate);
        if (aggregates.isEmpty()) {
            throw new IllegalStateException("재고 통계 집계 결과가 없어 스냅샷을 생성할 수 없습니다.");
        }

        return aggregates.stream()
                .map(aggregate -> insertSnapshot(syncJobId, asOfDate, aggregate))
                .toList();
    }

    private Long insertSnapshot(
            Long syncJobId,
            LocalDate asOfDate,
            InventoryStatisticsAggregateVO aggregate
    ) {
        Long snapshotId = snapshotMapper.selectNextSnapshotId();
        StatisticsSnapshotPayload payload = new StatisticsSnapshotPayload(
                InventoryStatisticsSummaryResponse.from(aggregate)
        );
        snapshotMapper.insertSnapshot(
                snapshotId,
                syncJobId,
                asOfDate,
                aggregate,
                PAYLOAD_VERSION,
                serialize(payload)
        );
        return snapshotId;
    }

    private String serialize(StatisticsSnapshotPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("통계 스냅샷 JSON 생성에 실패했습니다.", exception);
        }
    }
}
