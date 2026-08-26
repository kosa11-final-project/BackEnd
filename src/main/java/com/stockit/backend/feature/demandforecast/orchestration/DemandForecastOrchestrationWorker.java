package com.stockit.backend.feature.demandforecast.orchestration;

import static com.stockit.backend.feature.salesdaily.batch.SalesDailyCsvExportBatchConfiguration.BASE_DATE_PARAMETER;
import static com.stockit.backend.feature.salesdaily.batch.SalesDailyCsvExportBatchConfiguration.BLOB_URL_CONTEXT_KEY;
import static com.stockit.backend.feature.salesdaily.batch.SalesDailyCsvExportBatchConfiguration.EXPORT_MODE_PARAMETER;

import java.time.LocalDate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastOrchestrationMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

@Component
@ConditionalOnProperty(
        prefix = "app.demand-forecast.orchestration",
        name = "enabled",
        havingValue = "true"
)
public class DemandForecastOrchestrationWorker {
    private static final Logger log = LoggerFactory.getLogger(DemandForecastOrchestrationWorker.class);

    private final ExecutorService executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2),
            runnable -> {
                Thread thread = new Thread(runnable, "demand-forecast-orchestrator");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private final JobLauncher jobLauncher;
    private final ObjectProvider<Job> exportJob;
    private final DemandForecastRunControlService runControl;
    private final DemandForecastOrchestrationMapper orchestrationMapper;
    private final DemandForecastFastApiClient fastApiClient;

    public DemandForecastOrchestrationWorker(
            JobLauncher jobLauncher,
            @Qualifier("salesDailyCsvExportJob") ObjectProvider<Job> exportJob,
            DemandForecastRunControlService runControl,
            DemandForecastOrchestrationMapper orchestrationMapper,
            DemandForecastFastApiClient fastApiClient
    ) {
        this.jobLauncher = jobLauncher;
        this.exportJob = exportJob;
        this.runControl = runControl;
        this.orchestrationMapper = orchestrationMapper;
        this.fastApiClient = fastApiClient;
    }

    public boolean launchScheduled(LocalDate baseDate) {
        DemandForecastRunControlService.ScheduledRegistration registration =
                runControl.registerScheduled(baseDate);
        DemandForecastRunVO run = registration.run();
        if (!registration.created() || run == null || !"RUNNING".equals(run.getRunStatus())
                || !"EXPORTING".equals(run.getCurrentStage())) {
            return false;
        }
        try {
            executor.submit(() -> exportAndSubmit(run));
            return true;
        } catch (RejectedExecutionException exception) {
            runControl.fail(run, "ORCHESTRATOR_QUEUE_FULL", exception.getMessage());
            return false;
        }
    }

    private void exportAndSubmit(DemandForecastRunVO run) {
        try {
            Job job = exportJob.getIfAvailable();
            if (job == null) {
                throw new IllegalStateException("salesDailyCsvExportJob bean is unavailable");
            }
            JobExecution execution = jobLauncher.run(job, new JobParametersBuilder()
                    .addString(BASE_DATE_PARAMETER, run.getBaseDate().minusDays(1).toString(), true)
                    .addString(EXPORT_MODE_PARAMETER, "DAILY", true)
                    .addLong("forecastRunId", run.getForecastRunId(), true)
                    .toJobParameters());
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("SALES_DAILY export failed: " + execution.getStatus());
            }
            String blobUrl = execution.getExecutionContext().getString(BLOB_URL_CONTEXT_KEY, null);
            if (blobUrl == null || blobUrl.isBlank()) {
                throw new IllegalStateException("Azure Blob URL is missing from export execution context");
            }
            Long systemUserId = runControl.requiredSystemUserId();
            if (orchestrationMapper.markExportCompleted(
                    run.getForecastRunId(), execution.getId(), blobUrl, systemUserId
            ) != 1) {
                throw new IllegalStateException("forecast run is no longer exportable");
            }
            run.setCurrentStage("AZURE_SUBMITTING");

            DemandForecastFastApiClient.SubmitResponse response = fastApiClient.submit(run.getBaseDate());
            if (response == null || response.azureJobId() == null || response.azureJobId().isBlank()) {
                throw new IllegalStateException("FastAPI did not return an Azure Job ID");
            }
            run.setAzureJobId(response.azureJobId());
            if (orchestrationMapper.markAzureSubmitted(
                    run.getForecastRunId(), response.azureJobId(), systemUserId
            ) != 1) {
                throw new IllegalStateException("forecast run could not enter Azure polling");
            }
            log.info("Demand forecast Azure job submitted. runId={}, azureJobId={}",
                    run.getForecastRunId(), response.azureJobId());
        } catch (Exception exception) {
            runControl.fail(run, "EXPORT_OR_SUBMIT_FAILED", safeMessage(exception));
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
