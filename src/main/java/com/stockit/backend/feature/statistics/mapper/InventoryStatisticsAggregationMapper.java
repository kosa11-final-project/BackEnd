package com.stockit.backend.feature.statistics.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;

@Mapper
public interface InventoryStatisticsAggregationMapper {

    List<InventoryStatisticsAggregateVO> selectScopeAggregates(
            @Param("asOfDate") LocalDate asOfDate
    );
}
