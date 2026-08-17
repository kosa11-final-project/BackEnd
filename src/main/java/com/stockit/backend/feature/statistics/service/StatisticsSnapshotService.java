package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsSnapshotService {

    /**
     * 재고 동기화, 수요예측, 위험등급 산정이 완료된 후 범위별 재고 통계 스냅샷을 생성한다.
     * 같은 동기화 작업 ID로 다시 호출하면 기존 스냅샷 ID를 반환한다.
     */
    List<Long> createInventorySnapshots(Long syncJobId, LocalDate asOfDate);
}
