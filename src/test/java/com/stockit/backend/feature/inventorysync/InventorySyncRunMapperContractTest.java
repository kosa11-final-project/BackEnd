package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncRunMapperContractTest {

    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/inventorysync/InventorySyncRunMapper.xml"
    );

    @Test
    void successfulCompletionUsesOracleCompatibleNullBindings() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("#{errorCode,jdbcType=VARCHAR}")
                .contains("#{errorMessage,jdbcType=VARCHAR}");
    }

    @Test
    void insertResolvesTheOracleIdentityBeforeLaunchingTheWorker() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql).contains("<selectKey keyProperty=\"inventorySyncRunId\" resultType=\"long\" order=\"AFTER\">");
    }

    @Test
    void pessimisticLocksHaveAfiniteOracleWait() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("WHERE inventory_sync_run_id = #{runId} FOR UPDATE WAIT 5")
                .contains("SELECT 1 FROM inventory_sync_mutex\n         FOR UPDATE WAIT 5");
    }
}
