package com.stockit.backend.feature.demandforecast.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;

/** 파이프라인 트랜잭션과 분리하여 관리자 인앱 알림을 저장합니다. */
@Component
public class DemandForecastNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(DemandForecastNotificationListener.class);

    private final DemandForecastNotificationWriter writer;

    public DemandForecastNotificationListener(DemandForecastNotificationWriter writer) {
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyAdmins(DemandForecastRunNotificationEvent event) {
        // 실패 이벤트는 Teams 운영 채널 알림에 사용하되 인앱 notification에는 저장하지 않는다.
        if ("DEMAND_FORECAST_FAILED".equals(event.notificationType())) {
            return;
        }
        try {
            writer.write(event);
        } catch (RuntimeException exception) {
            log.error("Demand forecast notification failed after pipeline commit. runId={}",
                    event.forecastRunId(), exception);
        }
    }
}
