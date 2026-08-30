package com.stockit.backend.feature.strategy.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionSummaryVO;

@Mapper
public interface StrategyExecutionMapper {
    long countFinalStrategyExecutions(@Param("query") StrategyExecutionQuery query);

    StrategyExecutionSummaryVO selectFinalStrategyExecutionSummary(
            @Param("query") StrategyExecutionQuery query
    );

    List<StrategyExecutionBaseVO> selectFinalStrategyExecutions(
            @Param("query") StrategyExecutionQuery query
    );

    StrategyExecutionBaseVO selectFinalStrategyExecution(@Param("strategyCaseId") Long strategyCaseId);

    List<StrategyExecutionActionVO> selectSupportedActions(
            @Param("strategyOptionIds") List<Long> strategyOptionIds
    );

    List<StrategyExecutionInventoryVO> selectInventoryResults(
            @Param("strategyCaseId") Long strategyCaseId
    );

    List<StrategyExecutionDailySalesVO> selectDailySales(
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("asOfDate") LocalDate asOfDate
    );

    StrategyExecutionPerformanceVO selectPerformance(
            @Param("strategyOptionId") Long strategyOptionId
    );
}
