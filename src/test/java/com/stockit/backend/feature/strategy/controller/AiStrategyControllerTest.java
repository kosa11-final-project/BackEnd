package com.stockit.backend.feature.strategy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AiStrategyControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StrategyCaseService caseService;
    @MockitoBean private AiStrategyCaseQueryService queryService;

    @Test
    void acceptsGenerationRequestAndExposesLocation() throws Exception {
        when(caseService.createStrategyCase(any(), isNull())).thenReturn(
                new StrategyCaseCreated(
                        123L, "테스트 전략", StrategyCaseStatus.GENERATING,
                        null, LocalDateTime.of(2026, 8, 24, 10, 0)
                )
        );

        mockMvc.perform(post("/api/v1/ai-strategies")
                        .header("X-XSRF-TOKEN", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName":"테스트 전략",
                                  "skuId":1001,
                                  "sourceSalesPointId":10,
                                  "lotIds":[101,102],
                                  "candidateSalesPointIds":[20,30],
                                  "strategyTypes":["PRICE_DISCOUNT","RT_TRANSFER"],
                                  "preferredStartDate":"2026-08-24",
                                  "preferredEndDate":"2026-08-31"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/ai-strategies/123"))
                .andExpect(jsonPath("$.data.strategyCaseId").value(123))
                .andExpect(jsonPath("$.data.caseStatus").value("GENERATING"));
    }

    @Test
    void returnsCurrentGenerationStageAndOptionalResult() throws Exception {
        when(queryService.find(123L)).thenReturn(new AiStrategyCaseResponse(
                123L, 1001L, "테스트 전략", StrategyCaseStatus.GENERATING,
                null, null, null, null,
                LocalDateTime.of(2026, 8, 24, 10, 0), null, null
        ));

        mockMvc.perform(get("/api/v1/ai-strategies/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategyCaseId").value(123))
                .andExpect(jsonPath("$.data.caseStatus").value("GENERATING"))
                .andExpect(jsonPath("$.data.result").doesNotExist());
    }

    @Test
    void rejectsInvalidRequestBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/ai-strategies")
                        .header("X-XSRF-TOKEN", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void publishesCreateAndDetailEndpointsInSwagger() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies'].post.summary")
                        .value("AI 전략 생성 요청"))
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies/{strategyCaseId}'].get.summary")
                        .value("AI 전략 생성 상태·결과 조회"));
    }
}
