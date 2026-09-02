package com.stockit.backend.feature.demandforecast.orchestration;

import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class DemandForecastFastApiClient {
    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient restClient;
    private final DemandForecastOrchestrationProperties properties;

    public DemandForecastFastApiClient(
            RestClient.Builder builder,
            DemandForecastOrchestrationProperties properties
    ) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.resolvedConnectTimeout());
        requestFactory.setReadTimeout(properties.resolvedReadTimeout());
        this.restClient = builder
                .baseUrl(properties.requiredBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public SubmitResponse submit(LocalDate forecastBaseDate) {
        return restClient.post()
                .uri(properties.resolvedSubmitPath())
                .header(API_KEY_HEADER, key())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SubmitRequest(forecastBaseDate.toString(), "SCHEDULED"))
                .retrieve()
                .body(SubmitResponse.class);
    }

    public StatusResponse status(String azureJobId) {
        return restClient.get()
                .uri(properties.resolvedStatusPath(), azureJobId)
                .header(API_KEY_HEADER, key())
                .retrieve()
                .body(StatusResponse.class);
    }

    public void requestImport(String azureJobId) {
        restClient.post()
                .uri(properties.resolvedImportPath(), azureJobId)
                .header(API_KEY_HEADER, key())
                .retrieve()
                .toBodilessEntity();
    }

    public void requestDailyImport(String azureJobId) {
        restClient.post()
                .uri(properties.resolvedDailyImportPath(), azureJobId)
                .header(API_KEY_HEADER, key())
                .retrieve()
                .toBodilessEntity();
    }

    private String key() {
        return properties.fastApiKey() == null ? "" : properties.fastApiKey();
    }

    public record SubmitRequest(
            String forecastBaseDate,
            String triggerType
    ) {
    }

    public record SubmitResponse(
            String azureJobId,
            String status
    ) {
    }

    public record StatusResponse(String status, String errorMessage) {
    }
}
