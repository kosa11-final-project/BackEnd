package com.stockit.backend.feature.strategy.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseStatusCountVO;

@Mapper
public interface AiStrategyCaseListMapper {

    long countCases(
            @Param("query") AiStrategyCaseListQuery query,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("visibleFrom") LocalDateTime visibleFrom
    );

    List<AiStrategyCaseListVO> selectCases(
            @Param("query") AiStrategyCaseListQuery query,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("visibleFrom") LocalDateTime visibleFrom
    );

    List<AiStrategyCaseStatusCountVO> countCasesByStatus(
            @Param("query") AiStrategyCaseListQuery query,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("visibleFrom") LocalDateTime visibleFrom
    );
}
