package com.stockit.backend.feature.demandforecast.alert;

import java.time.Instant;
import java.time.LocalDate;

record DemandForecastTeamsAlertMessage(
        String title,
        String environment,
        Long forecastRunId,
        LocalDate baseDate,
        String failedStage,
        String errorCode,
        String errorMessage,
        String azureJobId,
        String schedulerName,
        Instant occurredAt,
        String deduplicationKey,
        String dashboardUrl
) {
}
