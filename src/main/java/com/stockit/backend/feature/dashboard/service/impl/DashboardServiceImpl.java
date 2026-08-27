package com.stockit.backend.feature.dashboard.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.dashboard.dto.DashboardSnapshotPayload;
import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OfflineStoreInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OnlineSalesPointInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.RiskSalesPointResponse;
import com.stockit.backend.feature.dashboard.dto.response.UrgentSkuResponse;
import com.stockit.backend.feature.dashboard.dto.response.WarehouseInventoryResponse;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.dashboard.mapper.DashboardMapper;
import com.stockit.backend.feature.dashboard.mapper.DashboardSnapshotMapper;
import com.stockit.backend.feature.dashboard.service.DashboardService;
import com.stockit.backend.feature.dashboard.vo.DashboardSnapshotVO;
import com.stockit.backend.feature.dashboard.vo.RiskSalesPointVO;
import com.stockit.backend.feature.dashboard.vo.UrgentSkuVO;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int SELLER_URGENT_SKU_PAYLOAD_VERSION = 3;

    private final DashboardMapper dashboardMapper;
    private final DashboardSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DashboardServiceImpl(
            DashboardMapper dashboardMapper,
            DashboardSnapshotMapper snapshotMapper,
            ObjectMapper objectMapper
    ) {
        this(dashboardMapper, snapshotMapper, objectMapper, Clock.system(KOREA_ZONE_ID));
    }

    DashboardServiceImpl(
            DashboardMapper dashboardMapper,
            DashboardSnapshotMapper snapshotMapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dashboardMapper = dashboardMapper;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public DashboardResponse getDashboard() {
        DashboardSnapshotVO snapshot = snapshotMapper.selectLatestSnapshot();
        if (snapshot == null) {
            throw new AppException(ErrorCode.DASHBOARD_SNAPSHOT_NOT_FOUND);
        }
        DashboardSnapshotPayload payload = deserialize(snapshot.getPayloadJson());
        if (snapshot.getPayloadVersion() < SELLER_URGENT_SKU_PAYLOAD_VERSION) {
            // 판매처별 긴급 SKU가 없는 레거시 스냅샷은 전체 TOP 5만으로 복원할 수 없으므로 최신 집계를 사용한다.
            return getLiveDashboard();
        }
        return payload.toResponse(snapshot.getCreatedAt().atZone(KOREA_ZONE_ID).toInstant());
    }

    @Override
    public DashboardResponse getLiveDashboard() {
        LocalDate asOfDate = LocalDate.now(clock.withZone(KOREA_ZONE_ID));
        return getLiveDashboard(asOfDate);
    }

    @Override
    public DashboardResponse getLiveDashboard(LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        DashboardSummaryResponse summary = DashboardSummaryResponse.from(
                dashboardMapper.selectSummary(asOfDate)
        );
        List<WarehouseInventoryResponse> warehouses = dashboardMapper
                .selectWarehouseInventories(asOfDate)
                .stream()
                .map(WarehouseInventoryResponse::from)
                .toList();
        List<OnlineSalesPointInventoryResponse> onlineSalesPoints = dashboardMapper
                .selectOnlineSalesPointInventories(asOfDate)
                .stream()
                .map(OnlineSalesPointInventoryResponse::from)
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
        Map<Long, List<UrgentSkuResponse>> urgentSkusBySalesPoint = rankUrgentSkusBySalesPoint(
                dashboardMapper.selectUrgentSkusBySalesPoint(asOfDate)
        );

        return new DashboardResponse(
                summary,
                warehouses,
                onlineSalesPoints,
                offlineStores,
                riskSalesPointsTop10,
                urgentSkusTop5,
                urgentSkusBySalesPoint,
                Instant.now(clock)
        );
    }

    private DashboardSnapshotPayload deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, DashboardSnapshotPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("대시보드 스냅샷 JSON 조회에 실패했습니다.", exception);
        }
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

    private static Map<Long, List<UrgentSkuResponse>> rankUrgentSkusBySalesPoint(List<UrgentSkuVO> values) {
        return values.stream()
                .filter(value -> value.getAllocatedSalesPointId() != null)
                .collect(Collectors.groupingBy(
                        UrgentSkuVO::getAllocatedSalesPointId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), DashboardServiceImpl::rankUrgentSkus)
                ));
    }
}
