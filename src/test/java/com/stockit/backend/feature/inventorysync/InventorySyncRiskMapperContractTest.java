package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncRiskMapperContractTest {

    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/inventorysync/InventorySyncRiskMapper.xml"
    );

    @Test
    void nullableRiskNumbersUseOracleNumericBindings() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("#{record.forecastId,jdbcType=NUMERIC}")
                .contains("#{record.expiryDaysLeft,jdbcType=NUMERIC}")
                .contains("#{record.holdingDays,jdbcType=NUMERIC}");
    }
}
