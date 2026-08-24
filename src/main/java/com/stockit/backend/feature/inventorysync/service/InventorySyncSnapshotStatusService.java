package com.stockit.backend.feature.inventorysync.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSnapshotStateVO;

/** 저장된 스냅샷 행을 기준으로 후속 대시보드·재고통계 최신화 상태를 계산합니다. */
@Service
public class InventorySyncSnapshotStatusService {
    private final InventorySyncRunMapper runMapper;

    public InventorySyncSnapshotStatusService(InventorySyncRunMapper runMapper) {
        this.runMapper = Objects.requireNonNull(runMapper, "runMapper");
    }

    public InventorySyncRunResponse.SnapshotRefresh resolve(InventorySyncRunVO run) {
        Objects.requireNonNull(run, "run");
        boolean required = "SUCCEEDED".equals(run.getRunStatus()) && run.getChangedCount() > 0;
        if (!required) {
            return InventorySyncRunResponse.SnapshotRefresh.notRequired();
        }

        Long runId = run.getInventorySyncRunId();
        InventorySyncSnapshotStateVO state = runMapper.selectSnapshotState(runId);
        return InventorySyncRunResponse.SnapshotRefresh.required(
                state != null && state.isDashboardReady(),
                state != null && state.isInventoryStatisticsReady(),
                state == null ? null : state.getDashboardStatus(),
                state == null ? null : state.getInventoryStatisticsStatus()
        );
    }
}
