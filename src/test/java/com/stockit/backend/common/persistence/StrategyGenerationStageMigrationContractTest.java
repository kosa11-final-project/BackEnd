package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StrategyGenerationStageMigrationContractTest {

    private static final String MIGRATION_PATH =
            "db/migration/V19__add_strategy_generation_stage.sql";

    @Test
    void addsNullableGenerationStageWithSupportedValuesOnly() throws IOException {
        String migration = readResource(MIGRATION_PATH);

        assertThat(migration).contains(
                "ALTER TABLE strategy_case ADD (",
                "generation_stage VARCHAR2(30)",
                "CONSTRAINT ck_strategy_case_gen_stage",
                "generation_stage IS NULL",
                "'FORECASTING'",
                "'STRATEGY_GENERATING'",
                "'COMPARISON_READY'"
        );
        assertThat(migration).doesNotContain("generation_stage VARCHAR2(30) NOT NULL");
    }

    @Test
    void documentsGenerationStageMeaning() throws IOException {
        String migration = readResource(MIGRATION_PATH);

        assertThat(migration).contains(
                "COMMENT ON COLUMN strategy_case.generation_stage",
                "Worker 처리 전에는 NULL"
        );
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = StrategyGenerationStageMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
