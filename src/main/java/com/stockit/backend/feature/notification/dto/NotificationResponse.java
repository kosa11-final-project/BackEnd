package com.stockit.backend.feature.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String notificationType,
        String severity,
        String title,
        String message,
        Long forecastRunId,
        boolean read,
        LocalDateTime createdAt
) {
}
