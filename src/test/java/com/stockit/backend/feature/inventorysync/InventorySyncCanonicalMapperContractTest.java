package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InventorySyncCanonicalMapperContractTest {

    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/inventorysync/InventorySyncCanonicalMapper.xml"
    );

    @Test
    void publishesEveryMappedCanonicalTargetWithChangedOnlyAuditUpdates() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("MERGE INTO product target")
                .contains("MERGE INTO sku target")
                .contains("MERGE INTO sku_channel_price target")
                .contains("MERGE INTO sku_cost target")
                .contains("MERGE INTO lot target")
                .contains("MERGE INTO inventory_policy target")
                .contains("MERGE INTO inventory_balance target")
                .contains("target.category_id = source.category_id")
                .contains("target.updated_by = #{actorId}")
                .doesNotContain("target.total_qty");
    }

    @Test
    void nullableOracleBindingsAreExplicit() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("#{record.brandName,jdbcType=VARCHAR}")
                .contains("#{record.paymentFee,jdbcType=NUMERIC}")
                .contains("#{record.priceEffectiveTo,jdbcType=DATE}")
                .contains("#{record.saleStopDate,jdbcType=DATE}")
                .contains("#{record.targetStockQty,jdbcType=NUMERIC}");
    }
}
