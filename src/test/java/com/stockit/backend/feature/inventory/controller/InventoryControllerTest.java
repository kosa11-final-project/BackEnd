package com.stockit.backend.feature.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.common.exception.GlobalExceptionHandler;
import com.stockit.backend.feature.inventory.dto.response.InventoryItemResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryFilterOptionsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryOptionResponse;
import com.stockit.backend.feature.inventory.dto.response.InventorySummaryResponse;
import com.stockit.backend.feature.inventory.dto.response.RiskResponse;
import com.stockit.backend.feature.inventory.dto.response.SalesPointResponse;
import com.stockit.backend.feature.inventory.service.InventoryQueryService;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;

@WebMvcTest(controllers = {InventoryController.class, InventoryFilterOptionsController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryQueryService inventoryQueryService;

    @Test
    void returnsListAndSummaryInFrontendResponseShape() throws Exception {
        InventoryItemResponse item = new InventoryItemResponse(
                "SKU-1",
                1001L,
                "P-1",
                "상품",
                "SKU-1",
                "규격",
                null,
                "GREETING",
                "SP-1",
                "판매처",
                "FROZEN",
                null,
                new BigDecimal("10"),
                new BigDecimal("8"),
                new BigDecimal("2"),
                null,
                "AVAILABLE",
                new RiskResponse("UNASSESSED", null, null),
                List.of(),
                0,
                List.of(
                        new SalesPointResponse(77L, "SP-1", "판매처 1", "GREETING", new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"), "SAFE", "센터")
                ),
                null,
                null,
                null,
                null,
                new BigDecimal("3")
        );
        given(inventoryQueryService.find(any(InventoryQuery.class)))
                .willReturn(new InventoryListResponse(List.of(item), 1, 1, 20, 1, false));
        given(inventoryQueryService.summary(any(InventoryQuery.class)))
                .willReturn(new InventorySummaryResponse(
                        new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"),
                        0, 0, 0, 0, null
                ));

        mockMvc.perform(get("/api/v1/inventories")
                        .param("channelType", "GREETING")
                        .param("filterOperator", "or")
                        .param("shortageYn", "Y")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rowId").value("SKU-1"))
                .andExpect(jsonPath("$.data.items[0].skuId").value(1001))
                .andExpect(jsonPath("$.data.items[0].salesPoints[0].salesPointCode").value("SP-1"))
                .andExpect(jsonPath("$.data.items[0].salesPoints[0].salesPointId").value(77))
                .andExpect(jsonPath("$.data.items[0].unassignedInventory").exists())
                .andExpect(jsonPath("$.data.items[0].expectedDisposalQuantity").value(3))
                .andExpect(jsonPath("$.data.items[0].risk.assessmentStatus").value("UNASSESSED"))
                .andExpect(jsonPath("$.data.totalCount").value(1));

        mockMvc.perform(get("/api/v1/inventories/summary")
                        .param("channelType", "GREETING")
                        .param("filterOperator", "or")
                        .param("shortageYn", "Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCurrentQuantity").value(10))
                .andExpect(jsonPath("$.data.totalAvailableQuantity").value(8));

        ArgumentCaptor<InventoryQuery> listQuery = ArgumentCaptor.forClass(InventoryQuery.class);
        ArgumentCaptor<InventoryQuery> summaryQuery = ArgumentCaptor.forClass(InventoryQuery.class);
        verify(inventoryQueryService).find(listQuery.capture());
        verify(inventoryQueryService).summary(summaryQuery.capture());
        assertThat(listQuery.getValue().filterOperator()).isEqualTo("OR");
        assertThat(listQuery.getValue().shortageYn()).isEqualTo("Y");
        assertThat(summaryQuery.getValue().filterOperator()).isEqualTo("OR");
        assertThat(summaryQuery.getValue().shortageYn()).isEqualTo("Y");
    }

    @Test
    void rejectsInvalidPageAndSizeBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/api/v1/inventories")
                        .param("page", "0")
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        mockMvc.perform(get("/api/v1/inventories")
                        .param("page", "1")
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void rejectsMalformedNumericQueryParametersAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/inventories")
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void rejectsUnsupportedFilterOperator() throws Exception {
        mockMvc.perform(get("/api/v1/inventories")
                        .param("filterOperator", "XOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void rejectsUnknownQueryParametersForListAndSummary() throws Exception {
        mockMvc.perform(get("/api/v1/inventories")
                        .param("unknownParam", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        mockMvc.perform(get("/api/v1/inventories/summary")
                        .param("unknownParam", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void returnsFilterOptionsDetailAndLotsUsingStableBusinessCodes() throws Exception {
        InventoryOptionResponse warehouse = new InventoryOptionResponse(
                "GYEONGIN_1", "경인 1센터", null, "CAPITAL", null,
                "ACTIVE", 10L, 20L, new BigDecimal("100"), null
        );
        given(inventoryQueryService.filterOptions()).willReturn(new InventoryFilterOptionsResponse(
                List.of(), List.of(), List.of(warehouse), List.of(), List.of(), List.of(), List.of(), List.of()
        ));
        InventoryLotResponse lot = new InventoryLotResponse(
                1L, "LOT-1", "AVAILABLE", new BigDecimal("10"), new BigDecimal("8"),
                new BigDecimal("2"), null, null, null, null, 10, 1, "GYEONGIN_1", "경인 1센터"
        );
        given(inventoryQueryService.detail("SKU-1", "GREETING")).willReturn(new InventoryDetailResponse(
                "SKU-1:GREETING", 1001L, "P-1", "상품", "SKU-1", "규격", null,
                "GREETING", "GREETING", "그리팅", "FROZEN", null,
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"), null,
                "AVAILABLE", new RiskResponse("UNASSESSED", null, null), List.of(), 0,
                List.of(new SalesPointResponse(77L, "GREETING", "그리팅", "GREETING", new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"), null, null)),
                1, 10, null, null, List.of(lot)
        ));
        given(inventoryQueryService.lots("SKU-1", "GREETING"))
                .willReturn(new InventoryLotsResponse(List.of(lot), 1));

        mockMvc.perform(get("/api/v1/inventories/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouses[0].code").value("GYEONGIN_1"))
                .andExpect(jsonPath("$.data.warehouses[0].availability").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/inventories/SKU-1/sales-points/GREETING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowId").value("SKU-1:GREETING"))
                .andExpect(jsonPath("$.data.skuId").value(1001))
                .andExpect(jsonPath("$.data.salesPoints[0].salesPointId").value(77))
                .andExpect(jsonPath("$.data.lots[0].lotNumber").value("LOT-1"));

        mockMvc.perform(get("/api/v1/inventories/SKU-1/sales-points/GREETING/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].fefoPriority").value(1));
    }
}
