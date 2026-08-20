package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;

@Mapper
public interface StrategyExecutionMapper {
    List<StrategyExecutionBaseVO> selectFinalStrategyExecutions();

    StrategyExecutionBaseVO selectFinalStrategyExecution(@Param("strategyCaseId") Long strategyCaseId);

    List<StrategyExecutionActionVO> selectSupportedActions(
            @Param("strategyOptionIds") List<Long> strategyOptionIds
    );

    List<StrategyExecutionInventoryVO> selectInventoryResults(
            @Param("strategyCaseId") Long strategyCaseId
    );

    List<StrategyExecutionDailySalesVO> selectDailySales(
            @Param("strategyCaseId") Long strategyCaseId
    );

    StrategyExecutionPerformanceVO selectPerformance(
            @Param("strategyOptionId") Long strategyOptionId
    );
}
