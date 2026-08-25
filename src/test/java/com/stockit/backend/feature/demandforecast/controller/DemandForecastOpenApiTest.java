package com.stockit.backend.feature.demandforecast.controller;

import static com.stockit.backend.feature.auth.security.InternalApiSecurityConstants.INTERNAL_API_KEY_HEADER;
import static org.hamcrest.Matchers.hasItems;
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
        String forecastExample = operation
                + ".requestBody.content['application/json'].examples"
                + "['LightGBM 예측 결과'].value.forecasts[0]";
        String forecastSchema = "$.components.schemas.DemandForecastImportItemRequest";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary")
                        .value("위험등급 판정용 누적 수요예측 적재 API"))
                .andExpect(jsonPath(forecastExample + ".skuId").value(101))
                .andExpect(jsonPath(forecastExample + ".predictedQtyD7").value(28.5))
                .andExpect(jsonPath(forecastExample + ".predictedQtyD14").value(55.0))
                .andExpect(jsonPath(forecastExample + ".predictedQtyD30").value(120.0))
                .andExpect(jsonPath(forecastExample + ".predictedQtyD60").value(240.0))
                .andExpect(jsonPath(forecastExample + ".predictedQtyD90").value(360.0))
                .andExpect(jsonPath(forecastExample + ".forecastSource").value("LIGHTGBM"))
                .andExpect(jsonPath(forecastExample + ".confidenceLevel").value("HIGH"))
                .andExpect(jsonPath(forecastExample + ".d7CumulativeQty").doesNotExist())
                .andExpect(jsonPath(forecastExample + ".d14CumulativeQty").doesNotExist())
                .andExpect(jsonPath(forecastExample + ".d30CumulativeQty").doesNotExist())
                .andExpect(jsonPath(forecastExample + ".d60CumulativeQty").doesNotExist())
                .andExpect(jsonPath(forecastExample + ".d90CumulativeQty").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath(operation + ".responses['409']").exists())
                .andExpect(jsonPath(operation + ".responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportRequest"
                        + ".properties.forecasts.maxItems").value(1000))
                .andExpect(jsonPath("$.components.schemas.DemandForecastImportItemRequest"
                        + ".properties.skuId.example").value(101))
                .andExpect(jsonPath(forecastSchema + ".properties.predictedQtyD7").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.predictedQtyD14").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.predictedQtyD30").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.predictedQtyD60").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.predictedQtyD90").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.forecastSource").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.confidenceLevel").exists())
                .andExpect(jsonPath(forecastSchema + ".properties.historyDays").doesNotExist())
                .andExpect(jsonPath(forecastSchema + ".properties.fallbackReason").doesNotExist())
                .andExpect(jsonPath(forecastSchema + ".required").value(hasItems(
                        "predictedQtyD7",
                        "predictedQtyD14",
                        "predictedQtyD30",
                        "predictedQtyD60",
                        "predictedQtyD90",
                        "forecastSource",
                        "confidenceLevel"
                )))
                .andExpect(jsonPath(operation + ".security[0].internalApiKey").exists())
                .andExpect(jsonPath("$.components.securitySchemes.internalApiKey.type")
                        .value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.internalApiKey.in")
                        .value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.internalApiKey.name")
                        .value(INTERNAL_API_KEY_HEADER));
    }
}
