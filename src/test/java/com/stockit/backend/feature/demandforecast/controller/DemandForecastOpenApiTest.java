package com.stockit.backend.feature.demandforecast.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DemandForecastOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsImportContractWithValidExampleAndErrorResponses() throws Exception {
        String operation = "$.paths['/api/v1/demand-forecasts/import'].post";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary")
                        .value("수요예측 결과 일괄 적재"))
                .andExpect(jsonPath(operation
                        + ".requestBody.content['application/json'].examples"
                        + "['LightGBM 예측 결과'].value.forecasts[0].skuId")
                        .value(101))
                .andExpect(jsonPath(operation
                        + ".requestBody.content['application/json'].examples"
                        + "['LightGBM 예측 결과'].value.forecasts[0].forecastSource")
                        .value("LIGHTGBM"))
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath(operation + ".responses['409']").exists())
                .andExpect(jsonPath(operation + ".responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportRequest"
                        + ".properties.forecasts.maxItems").value(1000))
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportItemRequest"
                        + ".properties.skuId.example").value(101))
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportItemRequest"
                        + ".properties.historyDays").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportItemRequest"
                        + ".properties.fallbackReason").doesNotExist());
    }
}
