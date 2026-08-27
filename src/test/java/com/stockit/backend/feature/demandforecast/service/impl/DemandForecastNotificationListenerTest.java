package com.stockit.backend.feature.demandforecast.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;

@ExtendWith(MockitoExtension.class)
class DemandForecastNotificationListenerTest {

    @Mock
    private DemandForecastNotificationWriter writer;

    @InjectMocks
    private DemandForecastNotificationListener listener;

    @Test
    void skipsInAppNotificationForFailedForecastRun() {
        DemandForecastRunNotificationEvent event = new DemandForecastRunNotificationEvent(
                10L,
                "DEMAND_FORECAST_FAILED",
                "ERROR",
                "일일 수요예측 실패",
                "수요예측 파이프라인이 실패했습니다.",
                "DEMAND_FORECAST:10:FAILED"
        );

        listener.notifyAdmins(event);

        verify(writer, never()).write(event);
    }

    @Test
    void persistsInAppNotificationForCompletedForecastRun() {
        DemandForecastRunNotificationEvent event = new DemandForecastRunNotificationEvent(
                10L,
                "DEMAND_FORECAST_COMPLETED",
                "INFO",
                "일일 수요예측 완료",
                "수요예측 결과가 정상 반영되었습니다.",
                "DEMAND_FORECAST:10:COMPLETED"
        );

        listener.notifyAdmins(event);

        verify(writer).write(event);
    }
}
