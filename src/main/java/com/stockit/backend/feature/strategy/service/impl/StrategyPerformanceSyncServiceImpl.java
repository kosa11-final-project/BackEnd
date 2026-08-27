package com.stockit.backend.feature.strategy.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.service.StrategyExecutionResultService;
import com.stockit.backend.feature.inventorysync.InventorySyncLockSupport;
import com.stockit.backend.feature.strategy.dto.response.StrategyPerformanceSyncResponse;
import com.stockit.backend.feature.strategy.mapper.StrategyPerformanceSyncMapper;
import com.stockit.backend.feature.strategy.service.StrategyPerformanceSyncService;
import com.stockit.backend.feature.strategy.vo.StrategyPerformanceSyncRowVO;

@Service
public class StrategyPerformanceSyncServiceImpl implements StrategyPerformanceSyncService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final StrategyPerformanceSyncMapper mapper;
    private final StrategyExecutionResultService resultService;
    private final Clock clock;

    @Autowired
    public StrategyPerformanceSyncServiceImpl(
            StrategyPerformanceSyncMapper mapper,
            StrategyExecutionResultService resultService
    ) {
        this(mapper, resultService, Clock.system(BUSINESS_ZONE));
    }

    StrategyPerformanceSyncServiceImpl(
            StrategyPerformanceSyncMapper mapper,
            StrategyExecutionResultService resultService,
            Clock clock
    ) {
        this.mapper = mapper;
        this.resultService = resultService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StrategyPerformanceSyncResponse synchronize(Long requestedBy) {
        if (requestedBy == null || requestedBy <= 0) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
        try {
            if (mapper.lockSyncMutex() != 1) {
                throw new IllegalStateException("strategy performance sync mutex is not initialized");
            }
        } catch (DataAccessException exception) {
            if (InventorySyncLockSupport.isLockWaitFailure(exception)) {
                throw new AppException(
                        ErrorCode.AI_STRATEGY_PERFORMANCE_SYNC_CONFLICT,
                        "다른 전략 성과 동기화가 진행 중입니다. 잠시 후 다시 시도해 주세요."
                );
            }
            throw exception;
        }

        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        int eligibleCount = mapper.countEligibleSelections(businessDate);
        List<StrategyPerformanceSyncRowVO> rows = mapper.selectPerformanceRows(businessDate);
        Map<Long, List<StrategyPerformanceSyncRowVO>> rowsBySelection = new LinkedHashMap<>();
        for (StrategyPerformanceSyncRowVO row : rows) {
            rowsBySelection.computeIfAbsent(row.getFinalSelectionId(), ignored -> new ArrayList<>()).add(row);
        }

        List<Long> processableIds = rowsBySelection.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .allMatch(row -> row.getActualContributionMargin() != null))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        int missingMarginCount = rowsBySelection.size() - processableIds.size();

        if (!processableIds.isEmpty()) {
            mapper.lockFinalSelections(processableIds);
        }

        int updatedRows = 0;
        for (Long finalSelectionId : processableIds) {
            for (StrategyPerformanceSyncRowVO row : rowsBySelection.get(finalSelectionId)) {
                int updated = mapper.updatePerformance(row, requestedBy);
                if (updated == 0) {
                    updated = mapper.insertPerformanceIfAbsent(row, requestedBy);
                }
                updatedRows += updated;
            }
        }

        Instant syncedAt = clock.instant();
        if (!processableIds.isEmpty()) {
            mapper.updateLastSyncedAt(processableIds, requestedBy, syncedAt);
        }
        resultService.process(businessDate);

        List<String> warnings = missingMarginCount == 0
                ? List.of()
                : List.of("유효한 가격·원가 정보가 없는 전략 " + missingMarginCount + "건은 동기화하지 않았습니다.");
        return new StrategyPerformanceSyncResponse(
                syncedAt,
                processableIds.size(),
                updatedRows,
                Math.max(0, eligibleCount - processableIds.size()),
                warnings
        );
    }
}
