package com.stockit.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.ActiveProfiles;

@JsonTest
@ActiveProfiles("test")
class ApiResponseJsonTest {

    @Autowired
    private JacksonTester<ApiResponse<Map<String, String>>> json;

    @Test
    void serializesSuccessResponseWithUtcTimestamp() throws Exception {
        ApiResponse<Map<String, String>> response = new ApiResponse<>(
                Map.of("name", "stockit"),
                Instant.parse("2026-08-12T00:00:00Z")
        );

        assertThat(json.write(response)).isStrictlyEqualToJson("""
                {
                  "data": {"name": "stockit"},
                  "timestamp": "2026-08-12T00:00:00Z"
                }
                """);
    }
}
