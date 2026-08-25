package com.stockit.backend.feature.demandforecast.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastNotificationMapper;

@Service
public class DemandForecastNotificationWriter {
    private final DemandForecastNotificationMapper mapper;

    public DemandForecastNotificationWriter(DemandForecastNotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(DemandForecastRunNotificationEvent event) {
        Long systemUserId = mapper.selectSystemUserId();
        if (systemUserId == null) {
            throw new IllegalStateException("__system__ user is required for demand forecast notifications");
        }
        mapper.insertAdminNotifications(
                event.forecastRunId(),
                event.notificationType(),
                event.severity(),
                event.title(),
                event.message(),
                event.deduplicationKey(),
                systemUserId
        );
    }
}
