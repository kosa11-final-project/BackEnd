package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StrategyNotificationMapperContractTest {

    private static final String STRATEGY_NOTIFICATION_MAPPER =
            "mappers/strategy/StrategyNotificationMapper.xml";
    private static final String NOTIFICATION_READER =
            "mappers/notification/NotificationMapper.xml";

    @Test
    void insertsStrategyReferenceAndPreventsDuplicateFinalNotifications()
            throws IOException {
        String mapper = readResource(STRATEGY_NOTIFICATION_MAPPER);

        assertThat(mapper).contains(
                "strategy_case_id",
                "forecast_run_id",
                "#{strategyCaseId}",
                "#{deduplicationKey}",
                "WHERE NOT EXISTS",
                "read_yn"
        );
    }

    @Test
    void exposesStrategyCaseIdForNotificationNavigation() throws IOException {
        assertThat(readResource(NOTIFICATION_READER))
                .contains("strategy_case_id");
    }

    private static String readResource(String path) throws IOException {
        ClassLoader classLoader = StrategyNotificationMapperContractTest.class
                .getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
