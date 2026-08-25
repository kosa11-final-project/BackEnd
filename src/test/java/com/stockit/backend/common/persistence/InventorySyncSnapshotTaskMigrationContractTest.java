package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InventorySyncSnapshotTaskMigrationContractTest {
    private static final String MIGRATION =
            "db/migration/V27__create_inventory_sync_snapshot_task.sql";

    @Test
    void definesBoundedDurableTasksWithoutChangingSnapshotTables() throws IOException {
        String sql = readResource(MIGRATION);

        assertThat(sql).contains(
                "CREATE TABLE inventory_sync_snapshot_task",
                "CONSTRAINT uq_isync_snapshot_task UNIQUE (inventory_sync_run_id, task_type)",
                "REFERENCES inventory_sync_run (inventory_sync_run_id) ON DELETE CASCADE",
                "task_type IN ('DASHBOARD', 'INVENTORY_STATISTICS')",
                "task_status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED')",
                "attempt_count <= max_attempts",
                "CREATE INDEX ix_isync_snapshot_task_due"
        );
        assertThat(sql).doesNotContain(
                "ALTER TABLE dashboard_snapshot",
                "ALTER TABLE statistics_snapshot",
                "CREATE MATERIALIZED VIEW"
        );
    }

    private static String readResource(String path) throws IOException {
        ClassLoader classLoader = InventorySyncSnapshotTaskMigrationContractTest.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) throw new IOException("Missing resource: " + path);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
