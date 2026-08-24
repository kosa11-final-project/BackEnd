package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncSnapshotTaskMapperContractTest {
    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/inventorysync/InventorySyncSnapshotTaskMapper.xml"
    );

    @Test
    void insertsAtMostOneTaskPerRunAndType() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql).contains(
                "WHEN dashboard_task_count = 0 THEN",
                "WHEN statistics_task_count = 0 THEN",
                "#{runId}, 'DASHBOARD', 'PENDING'",
                "#{runId}, 'INVENTORY_STATISTICS', 'PENDING'"
        );
    }

    @Test
    void claimsOnlyDueOrExpiredTasksWithAttemptsRemaining() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql).contains(
                "<update id=\"claimTask\">",
                "attempt_count = attempt_count + 1",
                "attempt_count &lt; max_attempts",
                "next_attempt_at &lt;= SYSTIMESTAMP",
                "lease_expires_at &lt;= SYSTIMESTAMP"
        );
    }

    @Test
    void terminalFailureIsNotSelectedForRecovery() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("task.task_status IN ('PENDING', 'RETRY_WAIT')")
                .contains("task.task_status = 'RUNNING'")
                .doesNotContain("task.task_status IN ('PENDING', 'RETRY_WAIT', 'FAILED')");
    }

    @Test
    void persistedRowsWinBeforeAnExhaustedLeaseIsFailed() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql).contains(
                "<update id=\"markPersistedSnapshotsSucceeded\">",
                "FROM dashboard_snapshot snapshot",
                "FROM statistics_snapshot snapshot",
                "<update id=\"markExpiredExhaustedTasksFailed\">",
                "SNAPSHOT_WORKER_LEASE_EXPIRED"
        );
    }
}
