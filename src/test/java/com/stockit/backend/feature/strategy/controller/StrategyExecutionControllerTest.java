package com.stockit.backend.feature.strategy.controller;

import static org.hamcrest.Matchers.nullValue;
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
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionPageResponse;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;

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
        StrategyExecutionQuery defaultQuery = new StrategyExecutionQuery(0, 10, null, null, null, "DESC");
        when(service.findAll(defaultQuery)).thenReturn(new StrategyExecutionPageResponse(
                List.of(response), 0, 10, 1, 1, true, true
        ));
        when(service.findByStrategyCaseId(101L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/strategy-executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(101))
                .andExpect(jsonPath("$.data.content[0].product.imageUrl").value("https://example.com/product.jpg"))
                .andExpect(jsonPath("$.data.content[0].lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));

        mockMvc.perform(get("/api/v1/strategy-executions/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.actions").isArray())
                .andExpect(jsonPath("$.data.inventoryTransfers[0].fromLocationId").value(501))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].fromLocationName").value("성남센터"))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].toLocationId").value(502))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].toLocationName").value("경인1센터"))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].destinationWarehouseId").value(502))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].destinationWarehouseName").value("경인1센터"))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].targetSalesPointId").value(10))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].targetSalesPointName").value("그리팅몰"))
                .andExpect(jsonPath("$.data.inventoryTransfers[0].quantity").value(20))
                .andExpect(jsonPath("$.data.salesDaily").isArray());
    }

    @Test
    void normalizesAndCombinesListParameters() throws Exception {
        StrategyExecutionQuery query = new StrategyExecutionQuery(
                1, 20, "두부", "EXECUTING", "PRICE_DISCOUNT", "ASC"
        );
        when(service.findAll(query)).thenReturn(new StrategyExecutionPageResponse(
                List.of(), 1, 20, 21, 2, false, true
        ));

        mockMvc.perform(get("/api/v1/strategy-executions")
                        .param("page", "1")
                        .param("size", "20")
                        .param("query", "  두부  ")
                        .param("status", "executing")
                        .param("actionType", "price_discount")
                        .param("sort", "establishedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void rejectsInvalidOrUnknownListParameters() throws Exception {
        mockMvc.perform(get("/api/v1/strategy-executions").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        mockMvc.perform(get("/api/v1/strategy-executions").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        mockMvc.perform(get("/api/v1/strategy-executions").param("unexpected", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
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
                .andExpect(jsonPath("$.paths['/api/v1/strategy-executions'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/strategy-executions'].get.parameters[?(@.name == 'actionType')]").exists())
                .andExpect(jsonPath("$.paths['/api/v1/strategy-executions'].get.responses['400'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath(detailOperation + ".summary")
                        .value("AI 전략 실행 관제 상세 조회"))
                .andExpect(jsonPath("$.components.schemas.StrategyExecutionResponse.properties.inventoryTransfers")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.InventoryTransfer.properties.fromLocationId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.InventoryTransfer.properties.destinationWarehouseId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.InventoryTransfer.properties.targetSalesPointId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.InventoryTransfer.properties.quantity.description")
                        .value("이동 수량. 항상 양수"))
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
                List.of(new StrategyExecutionResponse.InventoryTransfer(
                        501L, "성남센터", 502L, "경인1센터",
                        502L, "경인1센터", 10L, "그리팅몰", new java.math.BigDecimal("20")
                )),
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
