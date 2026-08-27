package com.stockit.backend.feature.strategy.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.StrategyPerformanceSyncRowVO;

@Mapper
public interface StrategyPerformanceSyncMapper {
    int lockSyncMutex();

    int countEligibleSelections(@Param("businessDate") LocalDate businessDate);

    List<StrategyPerformanceSyncRowVO> selectPerformanceRows(@Param("businessDate") LocalDate businessDate);

    List<Long> lockFinalSelections(@Param("finalSelectionIds") List<Long> finalSelectionIds);

    int updatePerformance(
            @Param("row") StrategyPerformanceSyncRowVO row,
            @Param("actorId") Long actorId
    );

    int insertPerformanceIfAbsent(
            @Param("row") StrategyPerformanceSyncRowVO row,
            @Param("actorId") Long actorId
    );

    int updateLastSyncedAt(
            @Param("finalSelectionIds") List<Long> finalSelectionIds,
            @Param("actorId") Long actorId,
            @Param("syncedAt") Instant syncedAt
    );
}
