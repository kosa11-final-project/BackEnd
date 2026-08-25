package com.stockit.backend.feature.inventorysync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import com.stockit.backend.feature.inventorysync.batch.InventorySyncBatchJobExecutionListener;
import com.stockit.backend.feature.inventorysync.service.InventorySyncWorker;

import com.stockit.backend.feature.inventorysync.service.InventorySyncPublisher;

/** U4 worker 설정의 단일 primitive를 등록합니다. Spring Batch 메타데이터와 분리된 HTTP launcher는 만들지 않습니다. */
@Configuration
public class InventorySyncBatchConfiguration {

    @Bean
    public InventorySyncPublisher inventorySyncPublisher() {
        return new InventorySyncPublisher();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.inventory-sync", name = "batch-enabled", havingValue = "true", matchIfMissing = true)
    public Job inventorySyncMainJob(JobRepository jobRepository, @Qualifier("inventorySyncMainStep") Step inventorySyncMainStep,
                                    InventorySyncBatchJobExecutionListener listener) {
        return new JobBuilder("inventorySyncMainJob", jobRepository)
                .listener(listener)
                .start(inventorySyncMainStep)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.inventory-sync", name = "batch-enabled", havingValue = "true", matchIfMissing = true)
    public Step inventorySyncMainStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                     InventorySyncWorker worker) {
        return new StepBuilder("inventorySyncMainStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Long runId = chunkContext.getStepContext().getStepExecution().getJobParameters().getLong("runId");
                    TransactionTemplate nonTransactionalWorker = new TransactionTemplate(transactionManager);
                    nonTransactionalWorker.setPropagationBehavior(TransactionTemplate.PROPAGATION_NOT_SUPPORTED);
                    nonTransactionalWorker.execute(status -> {
                        worker.execute(runId);
                        return null;
                    });
                    return null;
                }, transactionManager)
                .build();
    }

}
