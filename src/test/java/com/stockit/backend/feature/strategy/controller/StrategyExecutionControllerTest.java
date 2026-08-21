package com.stockit.backend.feature.strategy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class StrategyExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyExecutionService service;

    @Test
    void exposesListAndDetailUsingStrategyCaseId() throws Exception {
        StrategyExecutionResponse response = response();
        when(service.findAll()).thenReturn(List.of(response));
        when(service.findByStrategyCaseId(101L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/strategy-executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].product.imageUrl").value("https://example.com/product.jpg"))
                .andExpect(jsonPath("$.data[0].lastSyncedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].sync").doesNotExist());

        mockMvc.perform(get("/api/v1/strategy-executions/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.actions").isArray())
                .andExpect(jsonPath("$.data.salesDaily").isArray());
    }

    @Test
    void returnsProjectStandardNotFoundForUnknownCase() throws Exception {
        when(service.findByStrategyCaseId(999L))
                .thenThrow(new AppException(ErrorCode.AI_STRATEGY_EXECUTION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/strategy-executions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_STRATEGY-010"));
    }

    @Test
    void publishesBothEndpointsInOpenApi() throws Exception {
        String detailOperation = "$.paths['/api/v1/strategy-executions/{strategyCaseId}'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/strategy-executions'].get.summary")
                        .value("AI 전략 실행 관제 목록 조회"))
                .andExpect(jsonPath(detailOperation + ".summary")
                        .value("AI 전략 실행 관제 상세 조회"))
                .andExpect(jsonPath(detailOperation + ".responses['200']").exists())
                .andExpect(jsonPath(detailOperation + ".responses['401'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(detailOperation + ".responses['403'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(detailOperation + ".responses['404'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }

    private static StrategyExecutionResponse response() {
        return new StrategyExecutionResponse(
                101L,
                "SC-101",
                "READY",
                new StrategyExecutionResponse.Product(
                        1L, "상품", "SKU-1", "https://example.com/product.jpg"
                ),
                LocalDate.of(2026, 5, 1),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
