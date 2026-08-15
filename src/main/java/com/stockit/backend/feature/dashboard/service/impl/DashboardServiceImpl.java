package com.stockit.backend.feature.dashboard.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OfflineStoreInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.RiskSalesPointResponse;
import com.stockit.backend.feature.dashboard.dto.response.UrgentSkuResponse;
import com.stockit.backend.feature.dashboard.dto.response.WarehouseInventoryResponse;
import com.stockit.backend.feature.dashboard.mapper.DashboardMapper;
import com.stockit.backend.feature.dashboard.service.DashboardService;
import com.stockit.backend.feature.dashboard.vo.RiskSalesPointVO;
import com.stockit.backend.feature.dashboard.vo.UrgentSkuVO;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    @Autowired
    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this(dashboardMapper, Clock.system(KOREA_ZONE_ID));
    }

    DashboardServiceImpl(DashboardMapper dashboardMapper, Clock clock) {
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    @Override
    public DashboardResponse getDashboard() {
        LocalDate asOfDate = LocalDate.now(clock.withZone(KOREA_ZONE_ID));

        DashboardSummaryResponse summary = DashboardSummaryResponse.from(
                dashboardMapper.selectSummary(asOfDate)
        );
        List<WarehouseInventoryResponse> warehouses = dashboardMapper
                .selectWarehouseInventories(asOfDate)
                .stream()
                .map(WarehouseInventoryResponse::from)
                .toList();
        List<OfflineStoreInventoryResponse> offlineStores = dashboardMapper
                .selectOfflineStoreInventories(asOfDate)
                .stream()
                .map(OfflineStoreInventoryResponse::from)
                .toList();
        List<RiskSalesPointResponse> riskSalesPointsTop10 = rankRiskSalesPoints(
                dashboardMapper.selectRiskSalesPointsTop10(asOfDate)
        );
        List<UrgentSkuResponse> urgentSkusTop5 = rankUrgentSkus(
                dashboardMapper.selectUrgentSkusTop5(asOfDate)
        );

        return new DashboardResponse(
                summary,
                warehouses,
                offlineStores,
                riskSalesPointsTop10,
                urgentSkusTop5,
                Instant.now(clock)
        );
    }

    private static List<RiskSalesPointResponse> rankRiskSalesPoints(List<RiskSalesPointVO> values) {
        return IntStream.range(0, values.size())
                .mapToObj(index -> RiskSalesPointResponse.from(index + 1, values.get(index)))
                .toList();
    }

    private static List<UrgentSkuResponse> rankUrgentSkus(List<UrgentSkuVO> values) {
        return IntStream.range(0, values.size())
                .mapToObj(index -> UrgentSkuResponse.from(index + 1, values.get(index)))
                .toList();
    }
}
