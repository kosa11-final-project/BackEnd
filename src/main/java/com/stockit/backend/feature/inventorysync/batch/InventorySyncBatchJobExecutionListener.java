package com.stockit.backend.feature.inventorysync.batch;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;

@Component
public class InventorySyncBatchJobExecutionListener implements JobExecutionListener {
    private final InventorySyncRunMapper runMapper;
    public InventorySyncBatchJobExecutionListener(InventorySyncRunMapper runMapper) { this.runMapper = runMapper; }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        Long runId = jobExecution.getJobParameters().getLong("runId");
        if (runId != null) runMapper.setMainBatchExecutionId(runId, jobExecution.getId());
    }
}
