package com.stockit.backend.feature.inventory.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.inventory.dto.response.RiskAssessmentDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.RiskReasonDto;
import com.stockit.backend.feature.inventory.service.RiskAssessmentService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("InventoryRiskController API 테스트")
class InventoryRiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskAssessmentService riskAssessmentService;

    @Test
    @DisplayName("GET /api/v1/inventories/{skuCode}/sales-points/{salesPointCode}/risk - 위험도 조회 성공")
    void getRisk_success() throws Exception {
        String skuCode = "SKU-001";
        String salesPointCode = "GREETING";

        RiskAssessmentDetailResponse response = new RiskAssessmentDetailResponse(
                "ASSESSED",
                "DANGER",
                "CRITICAL",
                "소비기한 30일 이하 임박 (22일 남음)",
                "v1.0.0",
                Instant.now(),
                LocalDate.of(2026, 8, 16),
                BigDecimal.valueOf(110),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(60),
                BigDecimal.valueOf(30),
                22,
                14,
                List.of(new RiskReasonDto(
                        "EXPIRY_CRITICAL",
                        "소비기한 30일 이하 임박 (22일 남음)",
                        "CRITICAL",
                        "nearestExpiryDays=22"
                )),
                BigDecimal.valueOf(20),
                "Y"
        );

        when(riskAssessmentService.getRiskAssessment(eq(skuCode), eq(salesPointCode))).thenReturn(response);

        mockMvc.perform(get("/api/v1/inventories/{skuCode}/sales-points/{salesPointCode}/risk", skuCode, salesPointCode)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assessmentStatus").value("ASSESSED"))
                .andExpect(jsonPath("$.data.riskGrade").value("DANGER"))
                .andExpect(jsonPath("$.data.dbRiskGrade").value("CRITICAL"))
                .andExpect(jsonPath("$.data.nearestExpiryDays").value(22))
                .andExpect(jsonPath("$.data.stockCoverageDays").value(20))
                .andExpect(jsonPath("$.data.shortageYn").value("Y"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
