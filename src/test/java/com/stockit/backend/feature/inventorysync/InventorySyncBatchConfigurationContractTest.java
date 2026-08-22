package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncBatchConfigurationContractTest {
    private static final Path CONFIG = Path.of("src/main/java/com/stockit/backend/feature/inventorysync/config/InventorySyncBatchConfiguration.java");
    private static final Path LISTENER = Path.of("src/main/java/com/stockit/backend/feature/inventorysync/batch/InventorySyncBatchJobExecutionListener.java");

    @Test
    void usesSpringBatchJobRepositoryAndPersistsJobExecutionLineage() throws IOException {
        String config = Files.readString(CONFIG);
        String listener = Files.readString(LISTENER);

        assertThat(config)
                .contains("JobRepository jobRepository")
                .contains("new JobBuilder(\"inventorySyncMainJob\", jobRepository)")
                .contains("new StepBuilder(\"inventorySyncMainStep\", jobRepository)")
                .contains("getJobParameters().getLong(\"runId\")");
        assertThat(listener)
                .contains("JobExecutionListener")
                .contains("setMainBatchExecutionId");
    }
}
