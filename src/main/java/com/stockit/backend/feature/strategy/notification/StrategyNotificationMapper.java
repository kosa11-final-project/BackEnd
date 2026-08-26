package com.stockit.backend.feature.strategy.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 전략 생성의 최종 완료·실패 인앱 알림을 영속화한다. */
@Mapper
public interface StrategyNotificationMapper {

    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("notificationType") String notificationType,
            @Param("severity") String severity,
            @Param("title") String title,
            @Param("message") String message,
            @Param("deduplicationKey") String deduplicationKey
    );
}
