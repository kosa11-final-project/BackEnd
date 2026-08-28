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

    @Test
    void canonicalLotStatusIgnoresSourceStatusAndUsesDatesThenIntegratedQuantity() throws Exception {
        String sql = Files.readString(MAPPER);
        String lotWriteSql = sql.substring(
                sql.indexOf("<update id=\"updateLots\">"),
                sql.indexOf("<update id=\"refreshLotStatuses\">")
        );
        String refreshSql = sql.substring(sql.indexOf("<update id=\"refreshLotStatuses\">"));

        assertThat(lotWriteSql)
                .doesNotContain("#{record.lotStatus")
                .doesNotContain("target.lot_status = source.lot_status");
        assertThat(refreshSql)
                .contains("LEAST(TRUNC(resolved.expiry_date), TRUNC(resolved.sale_stop_date))")
                .contains("THEN 'EXPIRED'")
                .contains("THEN 'SALE_STOPPED'")
                .contains("NVL(balance.total_quantity, 0) = 0 THEN 'DEPLETED'")
                .contains("ELSE 'AVAILABLE'")
                .contains("SUM(ib.on_hand_qty + ib.reserved_qty) AS total_quantity")
                .contains("NVL(target.lot_status, '__NULL__') != source.lot_status");
    }

    @Test
    void riskSnapshotFindsDateAndForecastDrivenScopesBeforeCanonicalRefresh() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/mappers/inventorysync/InventorySyncRiskSnapshotMapper.xml"
        ));

        assertThat(sql)
                .contains("selectScopesRequiringDailyRefresh")
                .contains("WITH candidate_scopes AS")
                .contains("NVL(rl.current_status, '__NULL__') != rl.resolved_status")
                .contains("rl.sale_end_date &gt; TRUNC(CAST(#{asOfDate} AS DATE))")
                .contains("rl.sale_end_date &lt;= TRUNC(CAST(#{asOfDate} AS DATE)) + 90")
                .contains("rr.assessed_date &lt; TRUNC(CAST(#{asOfDate} AS DATE))")
                .contains("NVL(rr.forecast_id, -1) != NVL(lf.forecast_id, -1)")
                .contains("lf.forecast_updated_at > rr.assessed_at")
                .contains("COALESCE(TO_CHAR(NVL(ib.stock_sales_point_id, ib.allocated_sales_point_id)), 'UNASSIGNED')");
    }
}
