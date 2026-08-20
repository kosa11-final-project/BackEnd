package com.stockit.backend.feature.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.dashboard.dto.DashboardSnapshotPayload;
import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OfflineStoreInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OnlineSalesPointInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.RiskSalesPointResponse;
import com.stockit.backend.feature.dashboard.dto.response.UrgentSkuResponse;
import com.stockit.backend.feature.dashboard.dto.response.WarehouseInventoryResponse;
import com.stockit.backend.feature.dashboard.mapper.DashboardMapper;
import com.stockit.backend.feature.dashboard.mapper.DashboardSnapshotMapper;
import com.stockit.backend.feature.dashboard.vo.DashboardSnapshotVO;
import com.stockit.backend.feature.dashboard.vo.DashboardSummaryVO;
import com.stockit.backend.feature.dashboard.vo.OfflineStoreInventoryVO;
import com.stockit.backend.feature.dashboard.vo.OnlineSalesPointInventoryVO;
import com.stockit.backend.feature.dashboard.vo.RiskSalesPointVO;
import com.stockit.backend.feature.dashboard.vo.UrgentSkuVO;
import com.stockit.backend.feature.dashboard.vo.WarehouseInventoryVO;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-08-15T01:05:00Z");
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 15);

    @Mock
    private DashboardMapper dashboardMapper;

    @Mock
    private DashboardSnapshotMapper snapshotMapper;

    private DashboardServiceImpl dashboardService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(CALCULATED_AT, ZoneId.of("Asia/Seoul"));
        objectMapper = new ObjectMapper().findAndRegisterModules();
        dashboardService = new DashboardServiceImpl(
                dashboardMapper,
                snapshotMapper,
                objectMapper,
                clock
        );
    }

    @Test
    void returnsLatestCompletedSnapshot() throws Exception {
        DashboardSnapshotVO snapshot = new DashboardSnapshotVO();
        snapshot.setDashboardSnapshotId(11L);
        snapshot.setPayloadVersion(2);
        snapshot.setPayloadJson(objectMapper.writeValueAsString(new DashboardSnapshotPayload(
                DashboardSummaryResponse.from(summary()),
                List.of(WarehouseInventoryResponse.from(warehouse())),
                List.of(OnlineSalesPointInventoryResponse.from(onlineSalesPoint())),
                List.of(OfflineStoreInventoryResponse.from(store())),
                List.of(RiskSalesPointResponse.from(1, riskPoint())),
                List.of(UrgentSkuResponse.from(1, urgentSku()))
        )));
        snapshot.setCreatedAt(LocalDateTime.ofInstant(CALCULATED_AT, ZoneId.of("Asia/Seoul")));

        when(snapshotMapper.selectLatestSnapshot()).thenReturn(snapshot);

        DashboardResponse response = dashboardService.getDashboard();

        assertDashboard(response);
        verify(snapshotMapper).selectLatestSnapshot();
    }

    @Test
    void rejectsDashboardRequestWhenCompletedSnapshotDoesNotExist() {
        when(snapshotMapper.selectLatestSnapshot()).thenReturn(null);

        assertThatThrownBy(dashboardService::getDashboard)
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DASHBOARD_SNAPSHOT_NOT_FOUND);
    }

    @Test
    void combinesLiveDashboardQueriesAndAssignsRanksWithoutExposingRiskScore() {
        when(dashboardMapper.selectSummary(AS_OF_DATE)).thenReturn(summary());
        when(dashboardMapper.selectWarehouseInventories(AS_OF_DATE)).thenReturn(List.of(warehouse()));
        when(dashboardMapper.selectOnlineSalesPointInventories(AS_OF_DATE))
                .thenReturn(List.of(onlineSalesPoint()));
        when(dashboardMapper.selectOfflineStoreInventories(AS_OF_DATE)).thenReturn(List.of(store()));
        when(dashboardMapper.selectRiskSalesPointsTop10(AS_OF_DATE)).thenReturn(List.of(riskPoint()));
        when(dashboardMapper.selectUrgentSkusTop5(AS_OF_DATE)).thenReturn(List.of(urgentSku()));

        DashboardResponse response = dashboardService.getLiveDashboard();

        assertDashboard(response);

        verify(dashboardMapper).selectSummary(AS_OF_DATE);
        verify(dashboardMapper).selectWarehouseInventories(AS_OF_DATE);
        verify(dashboardMapper).selectOnlineSalesPointInventories(AS_OF_DATE);
        verify(dashboardMapper).selectOfflineStoreInventories(AS_OF_DATE);
        verify(dashboardMapper).selectRiskSalesPointsTop10(AS_OF_DATE);
        verify(dashboardMapper).selectUrgentSkusTop5(AS_OF_DATE);
    }

    private static void assertDashboard(DashboardResponse response) {
        assertThat(response.calculatedAt()).isEqualTo(CALCULATED_AT);
        assertThat(response.summary().totalAvailableStock()).isEqualByComparingTo("4062");
        assertThat(response.summary().riskAndWarningSkuCount()).isEqualTo(12);
        assertThat(response.warehouses()).singleElement()
                .satisfies(value -> assertThat(value.warehouseCode()).isEqualTo("SEONGNAM"));
        assertThat(response.onlineSalesPoints()).singleElement()
                .satisfies(value -> {
                    assertThat(value.salesPointCode()).isEqualTo("GREETING");
                    assertThat(value.storageWarehouseCount()).isEqualTo(1);
                });
        assertThat(response.offlineStores()).singleElement()
                .satisfies(value -> assertThat(value.salesPointCode()).isEqualTo("DEPT_PANGYO"));
        assertThat(response.riskSalesPointsTop10()).singleElement()
                .satisfies(value -> assertThat(value.rank()).isEqualTo(1));
        assertThat(response.urgentSkusTop5()).singleElement()
                .satisfies(value -> {
                    assertThat(value.rank()).isEqualTo(1);
                    assertThat(value.stockLocationName()).isEqualTo("성남 스마트푸드센터");
                    assertThat(value.allocatedSalesPointCode()).isEqualTo("GREETING");
                    assertThat(value.allocatedSalesPointName()).isEqualTo("그리팅몰");
                });
    }

    private static DashboardSummaryVO summary() {
        DashboardSummaryVO value = new DashboardSummaryVO();
        value.setTotalAvailableStock(new BigDecimal("4062"));
        value.setCriticalSkuCount(5);
        value.setWarningSkuCount(7);
        value.setShortageSkuCount(9);
        value.setExpectedDisposalQty(new BigDecimal("519"));
        return value;
    }

    private static WarehouseInventoryVO warehouse() {
        WarehouseInventoryVO value = new WarehouseInventoryVO();
        value.setWarehouseId(1L);
        value.setWarehouseCode("SEONGNAM");
        value.setWarehouseName("성남 스마트푸드센터");
        value.setRegionCode("GYEONGGI");
        value.setAddress("경기도 성남시");
        value.setCurrentStock(new BigDecimal("956"));
        value.setAvailableStock(new BigDecimal("872"));
        value.setNearExpiryStock(new BigDecimal("68"));
        value.setOutboundStock(new BigDecimal("84"));
        value.setRiskSkuCount(5);
        return value;
    }

    private static OfflineStoreInventoryVO store() {
        OfflineStoreInventoryVO value = new OfflineStoreInventoryVO();
        value.setSalesPointId(13L);
        value.setSalesPointCode("DEPT_PANGYO");
        value.setSalesPointName("판교점");
        value.setRegionCode("GYEONGGI");
        value.setAddress("경기도 성남시");
        value.setCurrentStock(new BigDecimal("526"));
        value.setAvailableStock(new BigDecimal("472"));
        value.setNearExpiryStock(new BigDecimal("45"));
        value.setExpectedDisposalQty(new BigDecimal("38"));
        value.setRiskSkuCount(3);
        return value;
    }

    private static OnlineSalesPointInventoryVO onlineSalesPoint() {
        OnlineSalesPointInventoryVO value = new OnlineSalesPointInventoryVO();
        value.setSalesPointId(1L);
        value.setSalesPointCode("GREETING");
        value.setSalesPointName("그리팅몰");
        value.setRegionCode("ONLINE");
        value.setStorageWarehouseCount(1);
        value.setCurrentStock(new BigDecimal("900"));
        value.setAvailableStock(new BigDecimal("833"));
        value.setNearExpiryStock(new BigDecimal("74"));
        value.setExpectedDisposalQty(new BigDecimal("118"));
        value.setRiskSkuCount(5);
        return value;
    }

    private static RiskSalesPointVO riskPoint() {
        RiskSalesPointVO value = new RiskSalesPointVO();
        value.setSalesPointId(1L);
        value.setSalesPointCode("GREETING");
        value.setSalesPointName("그리팅몰");
        value.setChannelType("ONLINE");
        value.setRegionCode("ONLINE");
        value.setAvailableStock(new BigDecimal("833"));
        value.setRiskSkuCount(5);
        value.setExpectedDisposalQty(new BigDecimal("118"));
        value.setNearExpiryStock(new BigDecimal("74"));
        return value;
    }

    private static UrgentSkuVO urgentSku() {
        UrgentSkuVO value = new UrgentSkuVO();
        value.setSkuId(7L);
        value.setSkuCode("GF-SAL-GRN-05");
        value.setSkuName("그린믹스 · 5팩");
        value.setStockLocationType("WAREHOUSE");
        value.setStockLocationId(1L);
        value.setStockLocationCode("SEONGNAM");
        value.setStockLocationName("성남 스마트푸드센터");
        value.setAllocatedSalesPointId(1L);
        value.setAllocatedSalesPointCode("GREETING");
        value.setAllocatedSalesPointName("그리팅몰");
        value.setExpiryDaysLeft(12);
        value.setSaleStopDaysLeft(5);
        value.setExpectedDisposalQty(new BigDecimal("86"));
        value.setReasonMessage("소비기한 내 판매 소진이 어렵습니다.");
        return value;
    }
}
