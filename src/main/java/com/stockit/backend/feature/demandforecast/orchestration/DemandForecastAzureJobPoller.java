package com.stockit.backend.feature.demandforecast.orchestration;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastOrchestrationMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

/** FastAPI가 노출하는 Azure Job 상태를 조회하고 완료된 Job의 결과 전송을 요청합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.demand-forecast.orchestration",
        name = "enabled",
        havingValue = "true"
)
public class DemandForecastAzureJobPoller {
    private static final Logger log = LoggerFactory.getLogger(DemandForecastAzureJobPoller.class);

    private final DemandForecastOrchestrationMapper mapper;
    private final DemandForecastRunControlService runControl;
    private final DemandForecastFastApiClient fastApiClient;
    private final DemandForecastOrchestrationProperties properties;

    public DemandForecastAzureJobPoller(
            DemandForecastOrchestrationMapper mapper,
            DemandForecastRunControlService runControl,
            DemandForecastFastApiClient fastApiClient,
            DemandForecastOrchestrationProperties properties
    ) {
        this.mapper = mapper;
        this.runControl = runControl;
        this.fastApiClient = fastApiClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.demand-forecast.orchestration.poll-interval:30s}")
    public void poll() {
        for (DemandForecastRunVO run : mapper.selectTimedOutRuns(
                properties.resolvedJobTimeout().toSeconds()
        )) {
            failTimedOutRun(run);
        }
        for (DemandForecastRunVO run : mapper.selectAzurePollingRuns()) {
            poll(run);
        }
    }

    private void failTimedOutRun(DemandForecastRunVO run) {
        try {
            runControl.fail(
                    run.getForecastRunId(),
                    "PIPELINE_TIMED_OUT",
                    "수요예측 파이프라인이 제한 시간 안에 완료되지 않았습니다."
            );
        } catch (Exception exception) {
            log.warn("Demand forecast timeout handling failed. runId={}, azureJobId={}",
                    run.getForecastRunId(), run.getAzureJobId(), exception);
        }
    }

    private void poll(DemandForecastRunVO run) {
        try {
            DemandForecastFastApiClient.StatusResponse response = fastApiClient.status(run.getAzureJobId());
            if (response == null || response.status() == null) {
                throw new IllegalStateException("FastAPI returned an empty Azure Job status");
            }
            String status = response.status().trim().toUpperCase(Locale.ROOT);
            Long systemUserId = runControl.requiredSystemUserId();
            switch (status) {
                case "COMPLETED", "SUCCEEDED" -> {
                    fastApiClient.requestImport(run.getAzureJobId());
                    if (mapper.markImportRequested(run.getForecastRunId(), systemUserId) != 1) {
                        throw new IllegalStateException("forecast run could not enter import requesting");
                    }
                    log.info("Demand forecast import requested. runId={}, azureJobId={}",
                            run.getForecastRunId(), run.getAzureJobId());
                }
                case "FAILED", "CANCELED", "CANCELLED" -> runControl.fail(
                        run.getForecastRunId(),
                        "AZURE_JOB_" + status,
                        response.errorMessage()
                );
                default -> mapper.touchAzurePolling(run.getForecastRunId(), systemUserId);
            }
        } catch (Exception exception) {
            // A transient status request failure is retried by the next polling cycle.
            log.warn("Demand forecast Azure status polling failed. runId={}, azureJobId={}",
                    run.getForecastRunId(), run.getAzureJobId(), exception);
        }
    }
}
