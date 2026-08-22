package com.stockit.backend.feature.statistics.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.statistics.vo.StrategyStatisticsActionVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsScopeVO;

@Mapper
public interface StrategyStatisticsMapper {

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
