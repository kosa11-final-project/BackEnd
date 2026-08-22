package com.stockit.backend.feature.demandforecast.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DemandForecastNotificationMapper {
    Long selectSystemUserId();

    int insertAdminNotifications(
            @Param("runId") Long runId,
            @Param("notificationType") String notificationType,
            @Param("severity") String severity,
            @Param("title") String title,
            @Param("message") String message,
            @Param("deduplicationKey") String deduplicationKey,
            @Param("systemUserId") Long systemUserId
    );
}
