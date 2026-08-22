package com.stockit.backend.feature.statistics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.statistics.service.StrategyExecutionResultService;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsScheduledTrigger;

class StrategyStatisticsScheduledTriggerTest {

    @Test
    void delegatesTheBusinessDateToTheLifecycleService() {
        StrategyExecutionResultService service = mock(StrategyExecutionResultService.class);

        new StrategyStatisticsScheduledTrigger(service, "Asia/Seoul").trigger();

        verify(service).process(any(LocalDate.class));
    }
}
