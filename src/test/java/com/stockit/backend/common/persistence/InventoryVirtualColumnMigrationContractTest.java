package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InventoryVirtualColumnMigrationContractTest {

    @Test
    void keepsInventoryTotalQuantityAsAnOracleVirtualColumn() throws IOException {
        String migration = readResource("db/migration/V9__convert_calculated_columns_to_virtual.sql");

        assertThat(migration)
                .contains("ALTER TABLE inventory_balance")
                .contains("DROP COLUMN total_qty")
                .contains("GENERATED ALWAYS AS (on_hand_qty + reserved_qty) VIRTUAL")
                .contains("COMMENT ON COLUMN inventory_balance.total_qty");
    }

    @Test
    void doesNotReintroduceAStoredInventoryTotalColumn() throws IOException {
        String migration = readResource("db/migration/V9__convert_calculated_columns_to_virtual.sql");

        assertThat(migration).doesNotContain("total_qty NUMBER(15,3) DEFAULT 0 NOT NULL");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = InventoryVirtualColumnMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
