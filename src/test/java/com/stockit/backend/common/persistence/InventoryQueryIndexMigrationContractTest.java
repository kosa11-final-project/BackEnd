package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InventoryQueryIndexMigrationContractTest {

    private static final String MIGRATION = "db/migration/V29__add_inventory_query_indexes.sql";

    @Test
    void addsIndexesForInventoryScopeAndLatestFactLookupsWithoutMutatingRows() throws IOException {
        String sql = readResource(MIGRATION);

        assertThat(sql).contains(
                "CREATE INDEX ix_inv_balance_sku_scope",
                "CREATE INDEX ix_risk_balance_latest",
                "CREATE INDEX ix_inv_policy_scope_latest",
                "CREATE INDEX ix_sku_price_scope_latest"
        );
        assertThat(sql.toUpperCase()).doesNotContain("UPDATE ", "DELETE ", "MERGE INTO", "INSERT INTO");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = InventoryQueryIndexMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
