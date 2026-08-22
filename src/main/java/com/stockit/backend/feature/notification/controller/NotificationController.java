package com.stockit.backend.feature.notification.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.notification.dto.NotificationResponse;
import com.stockit.backend.feature.notification.dto.UnreadNotificationCountResponse;
import com.stockit.backend.feature.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getRecent(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ApiResponse.of(service.getRecent(principal.getUserId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ApiResponse.of(service.getUnreadCount(principal.getUserId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        service.markRead(notificationId, principal.getUserId());
        return ApiResponse.empty();
    }
}
