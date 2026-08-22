package com.stockit.backend.feature.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.notification.dto.NotificationResponse;
import com.stockit.backend.feature.notification.dto.UnreadNotificationCountResponse;
import com.stockit.backend.feature.notification.mapper.NotificationMapper;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    public List<NotificationResponse> getRecent(Long userId) {
        return mapper.selectRecentByUserId(userId);
    }

    public UnreadNotificationCountResponse getUnreadCount(Long userId) {
        return new UnreadNotificationCountResponse(mapper.countUnreadByUserId(userId));
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        if (mapper.markRead(notificationId, userId) != 1) {
            throw new AppException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다.");
        }
    }
}
