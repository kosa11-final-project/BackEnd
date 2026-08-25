package com.stockit.backend.feature.statistics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsResponse;
import com.stockit.backend.feature.statistics.service.InventoryStatisticsService;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryStatisticsService inventoryStatisticsService;
    @MockitoBean
    private StatisticsSnapshotService statisticsSnapshotService;

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsInventoryStatistics() throws Exception {
        when(inventoryStatisticsService.getInventoryStatistics(any(), any(), any(), any()))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/statistics/inventory")
                        .param("fromDate", "2026-08-11")
                        .param("toDate", "2026-08-17")
                        .param("scopeType", "NATIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value("2026-08-17"))
                .andExpect(jsonPath("$.data.trendScopeType").value("NATIONAL"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void createsInventoryStatisticsSnapshots() throws Exception {
        when(statisticsSnapshotService.createInventorySnapshots(101L, LocalDate.of(2026, 8, 17)))
                .thenReturn(List.of(1L, 2L));

        mockMvc.perform(post("/api/v1/statistics/inventory/snapshots")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "syncJobId": 101,
                                  "asOfDate": "2026-08-17"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotCount").value(2))
                .andExpect(jsonPath("$.data.snapshotIds[1]").value(2));
    }

    @Test
    @WithAnonymousUser
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/statistics/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @WithAnonymousUser
    void publishesStatisticsEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/statistics/inventory'].get.summary")
                        .value("재고 통계 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/statistics/inventory/snapshots'].post.summary")
                        .value("재고 통계 스냅샷 생성"))
                .andExpect(jsonPath("$.components.schemas.InventoryStatisticsResponse").exists());
    }

    private static InventoryStatisticsResponse response() {
        return new InventoryStatisticsResponse(
                LocalDate.of(2026, 8, 17),
                Instant.parse("2026-08-16T18:07:00Z"),
                true,
                "NATIONAL",
                "ALL",
                Map.of(),
                List.of(),
                List.of()
        );
    }
}
