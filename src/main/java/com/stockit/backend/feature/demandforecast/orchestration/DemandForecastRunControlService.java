package com.stockit.backend.feature.demandforecast.orchestration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastOrchestrationMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

@Service
public class DemandForecastRunControlService {
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DemandForecastOrchestrationMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public DemandForecastRunControlService(
            DemandForecastOrchestrationMapper mapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    public ScheduledRegistration registerScheduled(LocalDate baseDate) {
        Long systemUserId = requiredSystemUserId();
        String key = "DEMAND_FORECAST:SCHEDULED:" + ID_DATE.format(baseDate);
        String clientRequestId = "demand-forecast-scheduled-" + ID_DATE.format(baseDate);
        boolean created = true;
        try {
            mapper.insertScheduledRun(clientRequestId, key, baseDate, systemUserId);
        } catch (DuplicateKeyException ignored) {
            // Multiple application instances may fire the same cron; the schedule key is the guard.
            created = false;
        }
        return new ScheduledRegistration(mapper.selectByClientRequestId(clientRequestId), created);
    }

    @Transactional
    public void fail(Long runId, String errorCode, String errorMessage) {
        Long systemUserId = requiredSystemUserId();
        String safeMessage = safeMessage(errorMessage);
        if (mapper.markFailed(runId, errorCode, safeMessage, systemUserId) != 1) {
            return;
        }
        eventPublisher.publishEvent(new DemandForecastRunNotificationEvent(
                runId,
                "DEMAND_FORECAST_FAILED",
                "ERROR",
                "일일 수요예측 실패",
                "수요예측 파이프라인이 실패했습니다. 단계 코드: " + errorCode
                        + ", 원인: " + safeMessage,
                "DEMAND_FORECAST:" + runId + ":FAILED"
        ));
    }

    public Long requiredSystemUserId() {
        Long systemUserId = mapper.selectSystemUserId();
        if (systemUserId == null) {
            throw new IllegalStateException("__system__ user is required for demand forecast orchestration");
        }
        return systemUserId;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "원인을 확인할 수 없습니다.";
        }
        return message.substring(0, Math.min(2000, message.length()));
    }

    public record ScheduledRegistration(DemandForecastRunVO run, boolean created) {
    }
}
