package com.stockit.backend.feature.strategy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListPageResponse;
import com.stockit.backend.feature.strategy.dto.response.AdjustedAiStrategySimulationResponse;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseListService;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.simulation.StrategyAdjustmentSimulationService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiStrategyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private StrategyCaseService caseService;
    @MockitoBean private AiStrategyCaseQueryService queryService;
    @MockitoBean private AiStrategyCaseListService listService;
    @MockitoBean private StrategyAdjustmentSimulationService adjustmentSimulationService;

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void acceptsGenerationRequestAndExposesLocation() throws Exception {
        when(caseService.createStrategyCase(any(), isNull())).thenReturn(
                new StrategyCaseCreated(
                        123L, "테스트 전략", StrategyCaseStatus.GENERATING,
                        null, LocalDateTime.of(2026, 8, 24, 10, 0)
                )
        );

        CsrfCredentials csrf = requestCsrf();
        mockMvc.perform(createRequest(csrf, """
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
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsCurrentGenerationStageAndOptionalResult() throws Exception {
        when(queryService.find(123L)).thenReturn(new AiStrategyCaseResponse(
                123L, "테스트 전략", StrategyCaseStatus.GENERATING, null,
                new AiStrategyCaseResponse.Sku(
                        1001L, "SKU-1001", "테스트 상품", null,
                        new AiStrategyCaseResponse.Category(301L, "국·탕", 3)
                ),
                new AiStrategyCaseResponse.Requester(7L, "이주영"),
                null, null,
                LocalDateTime.of(2026, 8, 24, 10, 0), null, null,
                new AiStrategyCaseResponse.RequestConditions(
                        null, List.of(), List.of(), List.of(),
                        null, null,
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2027, 2, 19)
                ),
                null
        ));

        mockMvc.perform(get("/api/v1/ai-strategies/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategyCaseId").value(123))
                .andExpect(jsonPath("$.data.caseStatus").value("GENERATING"))
                .andExpect(jsonPath("$.data.sku.skuCode").value("SKU-1001"))
                .andExpect(jsonPath("$.data.requester.userName").value("이주영"))
                .andExpect(jsonPath("$.data.requestConditions.forecastStartDate")
                        .value("2026-08-24"))
                .andExpect(jsonPath("$.data.result").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsGoneWhenGeneratedDetailResultHasExpired() throws Exception {
        when(queryService.find(123L)).thenThrow(
                new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
        );

        mockMvc.perform(get("/api/v1/ai-strategies/123"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AI_STRATEGY-014"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void recalculatesAdjustedStrategyConditions() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 20);
        LocalDate end = LocalDate.of(2026, 8, 27);
        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                "CAND-1",
                new StrategyCandidateSimulation.Summary(
                        decimal("8"), decimal("680"), decimal("120"),
                        decimal("0.1765"), 8, decimal("2"), decimal("0"),
                        decimal("0"), decimal("20")
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        decimal("2"), decimal("80"), decimal("10"),
                        decimal("2"), decimal("0"), decimal("20")
                ),
                List.of(new StrategyCandidateSimulation.DailyPoint(
                        start, decimal("1"), decimal("9"), decimal("85"),
                        decimal("15")
                )),
                List.of()
        );
        when(adjustmentSimulationService.simulate(any(), any(), any()))
                .thenReturn(new AdjustedAiStrategySimulationResponse(
                        123L,
                        "CAND-1",
                        new AdjustedAiStrategySimulationResponse.AdjustedConditions(
                                decimal("10"), decimal("0.15"), decimal("85"),
                                start, end, decimal("20"),
                                SalesPointDiscountPolicy.SalesPointGroup.DEPARTMENT_STORE,
                                decimal("0.20")
                        ),
                        simulation
                ));

        CsrfCredentials csrf = requestCsrf();
        mockMvc.perform(post(
                        "/api/v1/ai-strategies/123/candidates/CAND-1/simulations"
                )
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionQuantity": 10,
                                  "discountRate": 0.15,
                                  "startDate": "2026-08-20",
                                  "endDate": "2026-08-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateId").value("CAND-1"))
                .andExpect(jsonPath("$.data.adjustedConditions.strategyPrice")
                        .value(85))
                .andExpect(jsonPath("$.data.adjustedConditions.salesPointGroup")
                        .value("DEPARTMENT_STORE"))
                .andExpect(jsonPath("$.data.simulation.dailySeries[0].date")
                        .value("2026-08-20"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void returnsGenerationCaseListWithStatusCounts() throws Exception {
        when(listService.findAll(any())).thenReturn(new AiStrategyCaseListPageResponse(
                List.of(),
                new AiStrategyCaseListPageResponse.StatusCounts(3, 1, 1, 1),
                0, 10, 0, 0, true, true
        ));

        mockMvc.perform(get("/api/v1/ai-strategies")
                        .queryParam("status", "GENERATED")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.all").value(3))
                .andExpect(jsonPath("$.data.statusCounts.generated").value(1))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void rejectsUnknownOrDuplicateListParameters() throws Exception {
        mockMvc.perform(get("/api/v1/ai-strategies").queryParam("unknown", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));

        mockMvc.perform(get("/api/v1/ai-strategies")
                        .queryParam("status", "GENERATED", "GENERATING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    @WithMockUser(roles = "GREENFOOD_ADMIN")
    void rejectsInvalidRequestBeforeServiceCall() throws Exception {
        mockMvc.perform(createRequest(requestCsrf(), "{\"skuId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void rejectsAnonymousCreateDetailAndListRequests() throws Exception {
        mockMvc.perform(createRequest(requestCsrf(), "{\"skuId\":1001}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        mockMvc.perform(get("/api/v1/ai-strategies/123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        mockMvc.perform(get("/api/v1/ai-strategies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    void rejectsNonAdminCreateAndDetailRequests() throws Exception {
        mockMvc.perform(createRequest(requestCsrf(), "{\"skuId\":1001}")
                        .with(user("branch").roles("BRANCH_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));

        mockMvc.perform(get("/api/v1/ai-strategies/123")
                        .with(user("branch").roles("BRANCH_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
    }

    @Test
    void publishesCreateAndDetailEndpointsInSwagger() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies'].post.summary")
                        .value("AI 전략 생성 요청"))
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies'].get.summary")
                        .value("AI 전략 생성 Case 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies/{strategyCaseId}'].get.summary")
                        .value("AI 전략 생성 상태·결과 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/ai-strategies/{strategyCaseId}/candidates/{candidateId}/simulations'].post.summary")
                        .value("AI 전략 조건 조정 시뮬레이션"));
    }

    private MockHttpServletRequestBuilder createRequest(
            CsrfCredentials csrf,
            String body
    ) {
        return post("/api/v1/ai-strategies")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private CsrfCredentials requestCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody)
                .path("data").path("token").asText();
        String headerName = objectMapper.readTree(responseBody)
                .path("data").path("headerName").asText();
        return new CsrfCredentials(
                result.getResponse().getCookie("XSRF-TOKEN"),
                token,
                headerName
        );
    }

    private record CsrfCredentials(Cookie cookie, String token, String headerName) {
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
