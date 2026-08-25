package com.stockit.backend.feature.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardSummaryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OfflineStoreInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.OnlineSalesPointInventoryResponse;
import com.stockit.backend.feature.dashboard.dto.response.RiskSalesPointResponse;
import com.stockit.backend.feature.dashboard.dto.response.UrgentSkuResponse;
import com.stockit.backend.feature.dashboard.dto.response.WarehouseInventoryResponse;
import com.stockit.backend.feature.dashboard.service.DashboardService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsDashboardForGreenfoodAdministrator() throws Exception {
        when(dashboardService.getDashboard()).thenReturn(dashboard());

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCurrentStock").value(4800))
                .andExpect(jsonPath("$.data.summary.totalAvailableStock").value(4062))
                .andExpect(jsonPath("$.data.summary.riskAndWarningSkuCount").value(12))
                .andExpect(jsonPath("$.data.warehouses[0].warehouseCode").value("SEONGNAM"))
                .andExpect(jsonPath("$.data.onlineSalesPoints[0].salesPointCode").value("GREETING"))
                .andExpect(jsonPath("$.data.offlineStores[0].salesPointCode").value("DEPT_PANGYO"))
                .andExpect(jsonPath("$.data.riskSalesPointsTop10[0].channelType").value("ONLINE"))
                .andExpect(jsonPath("$.data.urgentSkusTop5[0].stockLocationName")
                        .value("성남 스마트푸드센터"))
                .andExpect(jsonPath("$.data.urgentSkusTop5[0].allocatedSalesPointCode")
                        .value("GREETING"))
                .andExpect(jsonPath("$.data.urgentSkusBySalesPoint['1'][0].allocatedSalesPointCode")
                        .value("GREETING"))
                .andExpect(jsonPath("$.data.urgentSkusTop5[0].riskScore").doesNotExist())
                .andExpect(jsonPath("$.data.calculatedAt").value("2026-08-15T01:05:00Z"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithAnonymousUser
    void rejectsUnauthenticatedDashboardRequest() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsLiveDashboardForVerification() throws Exception {
        when(dashboardService.getLiveDashboard()).thenReturn(dashboard());

        mockMvc.perform(get("/api/v1/dashboard/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalAvailableStock").value(4062));
    }

    @Test
    @WithAnonymousUser
    void publishesDashboardEndpointInOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard'].get.summary")
                        .value("재고 운영 대시보드 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/dashboard/live'].get.summary")
                        .value("재고 운영 대시보드 실시간 집계 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/dashboard/live'].get.deprecated").value(true))
                .andExpect(jsonPath("$.components.schemas.DashboardResponse.properties.calculatedAt").exists())
                .andExpect(jsonPath("$.components.schemas.DashboardResponse.properties.onlineSalesPoints")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UrgentSkuResponse.properties.allocatedSalesPointCode")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UrgentSkuResponse.properties.riskScore").doesNotExist());
    }

    private static DashboardResponse dashboard() {
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                new BigDecimal("4800"),
                new BigDecimal("4062"),
                5,
                7,
                12,
                9,
                new BigDecimal("519")
        );
        WarehouseInventoryResponse warehouse = new WarehouseInventoryResponse(
                1L,
                "SEONGNAM",
                "성남 스마트푸드센터",
                "GYEONGGI",
                "경기도 성남시",
                new BigDecimal("956"),
                new BigDecimal("872"),
                new BigDecimal("68"),
                new BigDecimal("84"),
                5
        );
        OfflineStoreInventoryResponse store = new OfflineStoreInventoryResponse(
                13L,
                "DEPT_PANGYO",
                "판교점",
                "GYEONGGI",
                "경기도 성남시",
                new BigDecimal("526"),
                new BigDecimal("472"),
                new BigDecimal("45"),
                new BigDecimal("38"),
                3
        );
        OnlineSalesPointInventoryResponse onlineSalesPoint = new OnlineSalesPointInventoryResponse(
                1L,
                "GREETING",
                "그리팅몰",
                "ONLINE",
                null,
                1,
                new BigDecimal("900"),
                new BigDecimal("833"),
                new BigDecimal("74"),
                new BigDecimal("118"),
                5
        );
        RiskSalesPointResponse riskPoint = new RiskSalesPointResponse(
                1,
                1L,
                "GREETING",
                "그리팅몰",
                "ONLINE",
                "ONLINE",
                new BigDecimal("833"),
                5,
                new BigDecimal("118"),
                new BigDecimal("74")
        );
        UrgentSkuResponse urgentSku = new UrgentSkuResponse(
                1,
                7L,
                "GF-SAL-GRN-05",
                "그린믹스 · 5팩",
                "WAREHOUSE",
                1L,
                "SEONGNAM",
                "성남 스마트푸드센터",
                1L,
                "GREETING",
                "그리팅몰",
                12,
                5,
                new BigDecimal("86"),
                "소비기한 내 판매 소진이 어렵습니다."
        );
        return new DashboardResponse(
                summary,
                List.of(warehouse),
                List.of(onlineSalesPoint),
                List.of(store),
                List.of(riskPoint),
                List.of(urgentSku),
                Map.of(1L, List.of(urgentSku)),
                Instant.parse("2026-08-15T01:05:00Z")
        );
    }
}
