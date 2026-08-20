package com.stockit.backend.feature.dashboard.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.dashboard.vo.DashboardSummaryVO;
import com.stockit.backend.feature.dashboard.vo.OfflineStoreInventoryVO;
import com.stockit.backend.feature.dashboard.vo.OnlineSalesPointInventoryVO;
import com.stockit.backend.feature.dashboard.vo.RiskSalesPointVO;
import com.stockit.backend.feature.dashboard.vo.UrgentSkuVO;
import com.stockit.backend.feature.dashboard.vo.WarehouseInventoryVO;

@Mapper
public interface DashboardMapper {

    DashboardSummaryVO selectSummary(@Param("asOfDate") LocalDate asOfDate);

    List<WarehouseInventoryVO> selectWarehouseInventories(@Param("asOfDate") LocalDate asOfDate);

    List<OnlineSalesPointInventoryVO> selectOnlineSalesPointInventories(
            @Param("asOfDate") LocalDate asOfDate
    );

    List<OfflineStoreInventoryVO> selectOfflineStoreInventories(@Param("asOfDate") LocalDate asOfDate);

    List<RiskSalesPointVO> selectRiskSalesPointsTop10(@Param("asOfDate") LocalDate asOfDate);

    List<UrgentSkuVO> selectUrgentSkusTop5(@Param("asOfDate") LocalDate asOfDate);
}
