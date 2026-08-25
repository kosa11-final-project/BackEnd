package com.stockit.backend.feature.statistics.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsDailySalesVO;

@Mapper
public interface InventoryStatisticsAggregationMapper {

    List<InventoryStatisticsAggregateVO> selectScopeAggregates(
            @Param("asOfDate") LocalDate asOfDate
    );

    List<InventoryStatisticsDailySalesVO> selectNationalDailySales(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
