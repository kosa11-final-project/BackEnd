package com.stockit.backend.feature.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.notification.dto.NotificationResponse;

@Mapper
public interface NotificationMapper {
    List<NotificationResponse> selectRecentByUserId(@Param("userId") Long userId);

    long countUnreadByUserId(@Param("userId") Long userId);

    int markRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);
}
