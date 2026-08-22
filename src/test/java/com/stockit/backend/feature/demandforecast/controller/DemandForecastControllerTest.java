package com.stockit.backend.feature.demandforecast.controller;

import static com.stockit.backend.feature.auth.security.InternalApiSecurityConstants.INTERNAL_API_KEY_HEADER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.auth.vo.AuthUserVO;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;

@SpringBootTest(
        properties = {
                "app.internal-api.key=test-internal-api-key",
                "app.internal-api.user-id=99",
                "app.internal-api.principal-name=ml-service"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemandForecastControllerTest {

    private static final String API_KEY = "test-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemandForecastService demandForecastService;

    @Test
    void importsValidatedForecastBatchWithCommonResponse() throws Exception {
        when(demandForecastService.importForecasts(any(DemandForecastImportRequest.class), eq(99L)))
                .thenReturn(new DemandForecastImportResponse(
                        "purple_monkey_gyk4m5yyxr",
                        "stockit-demand-lightgbm",
                        "1",
                        7L,
                        LocalDate.of(2026, 7, 31),
                        1,
                        10,
                        10L,
                        1,
                        1,
                        1L,
                        false,
                        "RUNNING"
                ));

        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.azureJobId").value("purple_monkey_gyk4m5yyxr"))
                .andExpect(jsonPath("$.data.modelVersionId").value(7))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void acceptsExistingFastApiPayloadWithoutTotalItems() throws Exception {
        when(demandForecastService.importForecasts(any(DemandForecastImportRequest.class), eq(99L)))
                .thenReturn(new DemandForecastImportResponse(
                        "purple_monkey_gyk4m5yyxr",
                        "stockit-demand-lightgbm",
                        "1",
                        7L,
                        LocalDate.of(2026, 7, 31),
                        1,
                        1,
                        null,
                        1,
                        1,
                        1L,
                        false,
                        "SUCCEEDED"
                ));

        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()
                                .replace("\"totalBatches\": 10", "\"totalBatches\": 1")
                                .replace("                  \"totalItems\": 10,\n", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"));
    }

    @Test
    void rejectsNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody().replace("12.3", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'forecasts[0].predictedQtyD7')]").exists());
    }

    @Test
    void rejectsNonCumulativeQuantities() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody().replace("24.8", "10.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("예측값은 D7 ≤ D14 ≤ D30 ≤ D60 ≤ D90 순서여야 합니다."));
    }

    @Test
    void rejectsBatchNumberGreaterThanTotalBatches() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody().replace("\"batchNumber\": 1", "\"batchNumber\": 11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("배치 번호는 전체 배치 수보다 클 수 없습니다."));
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(demandForecastService, never()).importForecasts(any(), any());
    }

    @Test
    void rejectsInvalidApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header(INTERNAL_API_KEY_HEADER, "wrong-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(demandForecastService, never()).importForecasts(any(), any());
    }

    @Test
    void doesNotAllowSessionAuthenticationToBypassApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .with(authentication(adminAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(demandForecastService, never()).importForecasts(any(), any());
    }

    @Test
    void rejectsApiKeySentWithDifferentHeaderName() throws Exception {
        mockMvc.perform(post("/api/v1/demand-forecasts/import")
                        .header("X-Stockit-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        verify(demandForecastService, never()).importForecasts(any(), any());
    }

    @Test
    void doesNotUseApiKeyAuthenticationForOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/tmp/ping")
                        .header(INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    private static UsernamePasswordAuthenticationToken adminAuthentication() {
        AuthUserVO user = new AuthUserVO();
        user.setUserId(1L);
        user.setLoginId("admin");
        user.setUserName("관리자");
        user.setRoleCode("GREENFOOD_ADMIN");
        AuthPrincipal principal = AuthPrincipal.from(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private static String validRequestBody() {
        return """
                {
                  "azureJobId": "purple_monkey_gyk4m5yyxr",
                  "modelName": "stockit-demand-lightgbm",
                  "modelVersion": "1",
                  "forecastBaseDate": "2026-07-31",
                  "batchNumber": 1,
                  "totalBatches": 10,
                  "totalItems": 10,
                  "forecasts": [{
                    "skuId": 101,
                    "salesPointId": 10,
                    "predictedQtyD7": 12.3,
                    "predictedQtyD14": 24.8,
                    "predictedQtyD30": 51.2,
                    "predictedQtyD60": 103.7,
                    "predictedQtyD90": 157.1,
                    "forecastSource": "LIGHTGBM",
                    "confidenceLevel": "HIGH"
                  }]
                }
                """;
    }
}
