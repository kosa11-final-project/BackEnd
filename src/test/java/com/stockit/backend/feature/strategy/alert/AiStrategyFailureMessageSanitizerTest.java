package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiStrategyFailureMessageSanitizerTest {
    private final AiStrategyFailureMessageSanitizer sanitizer =
            new AiStrategyFailureMessageSanitizer();

    @Test
    void masksSecretsAndRemovesLineBreaks() {
        String result = sanitizer.sanitize(
                "Gemini failed\nAuthorization: Bearer top-secret; "
                        + "url=https://example.test?a=1&key=api-secret "
                        + "password=my-password"
        );

        assertThat(result)
                .doesNotContain("top-secret", "api-secret", "my-password", "\n")
                .contains("Authorization: ***", "&key=***", "password=***");
    }

    @Test
    void limitsMessageLength() {
        String result = sanitizer.sanitize("x".repeat(1100));

        assertThat(result).hasSize(1003).endsWith("...");
    }
}
