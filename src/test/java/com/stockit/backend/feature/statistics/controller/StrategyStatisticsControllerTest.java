package com.stockit.backend.feature.statistics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrategyStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyStatisticsService strategyStatisticsService;

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsStrategyStatistics() throws Exception {
        when(strategyStatisticsService.getStrategyStatistics(any(), any(), any(), any()))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/statistics/strategies")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31")
                        .param("scopeType", "NATIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.completedCount").value(2))
                .andExpect(jsonPath("$.data.scopeType").value("NATIONAL"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void publishesStrategyStatisticsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/statistics/strategies'].get.summary")
                        .value("AI 전략 성과 통계 조회"))
                .andExpect(jsonPath("$.components.schemas.StrategyStatisticsResponse").exists());
    }

    private static StrategyStatisticsResponse response() {
        return new StrategyStatisticsResponse(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "NATIONAL",
                "ALL",
                new StrategyStatisticsSummaryResponse(
                        2,
                        1,
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(90),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(16.6667),
                        BigDecimal.TEN,
                        BigDecimal.valueOf(1500)
                ),
                List.of(),
                List.of()
        );
    }
}
