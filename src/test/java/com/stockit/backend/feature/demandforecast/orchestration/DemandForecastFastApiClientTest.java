package com.stockit.backend.feature.demandforecast.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class DemandForecastFastApiClientTest {
    private static final String JOBS_PATH = "/api/v1/demand-forecasts/jobs";

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();

    private HttpServer server;
    private DemandForecastFastApiClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(JOBS_PATH, this::handle);
        server.start();
        DemandForecastOrchestrationProperties properties =
                new DemandForecastOrchestrationProperties(
                        true,
                        "http://localhost:" + server.getAddress().getPort(),
                        "test-api-key",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
        client = new DemandForecastFastApiClient(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void submitsUsingExistingFastApiContract() throws Exception {
        DemandForecastFastApiClient.SubmitResponse response =
                client.submit(LocalDate.of(2026, 8, 22));

        JsonNode json = objectMapper.readTree(requestBody.get());
        assertThat(response.azureJobId()).isEqualTo("forecast-job-001");
        assertThat(requestMethod).hasValue("POST");
        assertThat(json.path("forecastBaseDate").asText()).isEqualTo("2026-08-22");
        assertThat(json.path("triggerType").asText()).isEqualTo("SCHEDULED");
        assertThat(json.has("forecastRunId")).isFalse();
        assertThat(json.has("clientRequestId")).isFalse();
        assertThat(json.has("inputBlobUrl")).isFalse();
    }

    @Test
    void requestsImportWithoutLegacyRequestBody() {
        client.requestImport("forecast-job-001");

        assertThat(requestMethod).hasValue("POST");
        assertThat(requestBody).hasValue("");
    }

    @Test
    void requestsDailyImportWithoutRequestBody() {
        client.requestDailyImport("forecast-job-001");

        assertThat(requestMethod).hasValue("POST");
        assertThat(requestPath).hasValue(JOBS_PATH + "/forecast-job-001/daily-import");
        assertThat(requestBody).hasValue("");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestMethod.set(exchange.getRequestMethod());
        requestPath.set(exchange.getRequestURI().getPath());
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String response = exchange.getRequestURI().getPath().endsWith("/import")
                ? ""
                : """
                        {
                          "azureJobId": "forecast-job-001",
                          "displayName": "stockit-demand-forecast-20260822",
                          "status": "Queued",
                          "forecastBaseDate": "2026-08-22",
                          "triggerType": "SCHEDULED"
                        }
                        """;
        int status = response.isEmpty() ? 200 : 202;
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
