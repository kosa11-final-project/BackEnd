package com.stockit.backend.feature.statistics.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.statistics.vo.StrategyStatisticsActionVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionDueResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionStartCandidateVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsScopeVO;

@Mapper
public interface StrategyStatisticsMapper {

    List<StrategyExecutionStartCandidateVO> selectExecutionStartCandidates(
            @Param("businessDate") LocalDate businessDate
    );

    int insertExecutionStartResult(StrategyExecutionStartCandidateVO candidate);

    List<StrategyExecutionDueResultVO> selectDueExecutionResults(
            @Param("businessDate") LocalDate businessDate
    );

    int completeExecutionResult(
            @Param("result") StrategyExecutionDueResultVO result,
            @Param("achievementRate") BigDecimal achievementRate,
            @Param("estimatedLossSavingsAmount") BigDecimal estimatedLossSavingsAmount
    );

    List<StrategyStatisticsResultVO> selectCompletedResults(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    List<StrategyStatisticsActionVO> selectActionTypes(
            @Param("strategyOptionIds") List<Long> strategyOptionIds
    );

    List<StrategyStatisticsScopeVO> selectResultScopes(
            @Param("finalSelectionIds") List<Long> finalSelectionIds
    );
}
