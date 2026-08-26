package com.stockit.backend.feature.inventory.mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQuantitySqlContractTest {

    private static final Path MAPPER_ROOT = Path.of("src/main/resources/mappers");

    @Test
    void inventoryQueriesNeverSubtractReservedQuantityFromOnHandQuantity() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");
        String forecastSql = read("demandforecast/DemandForecastMapper.xml");
        String riskSql = read("inventory/RiskAssessmentMapper.xml");

        assertThat(inventorySql)
                .doesNotContain("on_hand_qty - reserved_qty")
                .doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(forecastSql).doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(riskSql).doesNotContain("ib.on_hand_qty - ib.reserved_qty");
        assertThat(forecastSql)
                .doesNotContain("SALES_DAILY")
                .doesNotContain("sales_daily");
        assertThat(riskSql)
                .doesNotContain("SALES_DAILY")
                .doesNotContain("sales_daily");
        assertThat(forecastSql).contains("SUM(ib.on_hand_qty)");
        assertThat(riskSql)
                .contains("SUM(ib.on_hand_qty) AS on_hand_qty")
                .doesNotContain("SUM(ib.reserved_qty)");
        assertThat(forecastSql)
                .contains("#{salesPointCode} != 'UNASSIGNED' AND sp.sales_point_code = #{salesPointCode}")
                .contains("#{salesPointCode} = 'UNASSIGNED'");
    }

    @Test
    void inventoryCurrentQuantityIncludesAvailableAndReservedBuckets() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql).contains("SUM(on_hand_qty + reserved_qty) AS current_qty");
    }

    @Test
    void liveAvailabilityExcludesExpiredSaleStoppedAndDepletedStockAcrossInventoryViews() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("lot_status IN ('EXPIRED', 'SALE_STOPPED', 'DEPLETED')")
                .contains("TRUNC(expiry_date) &lt;= TRUNC(CAST(#{asOfDate} AS DATE))")
                .contains("TRUNC(sale_stop_date) &lt;= TRUNC(CAST(#{asOfDate} AS DATE))")
                .contains("SUM(available_qty) AS total_available_qty")
                .contains("expected_disposal_qty");
        assertThat(occurrences(inventorySql, "SUM(CASE" )).isGreaterThanOrEqualTo(5);
    }

    @Test
    void inventoryDetailCalculatesExpectedDisposalForTheSelectedSalesPointScope() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");
        String detailSql = inventorySql.substring(
                inventorySql.indexOf("<select id=\"selectInventoryDetail\""),
                inventorySql.indexOf("<select id=\"countInventoryScope\"")
        );

        assertThat(detailSql)
                .contains("END AS sales_point_state,\n                   ib.allocated_sales_point_id,\n                   ib.warehouse_id")
                .contains("detail_disposal_scope AS")
                .contains("detail_disposal AS")
                .contains("ds.allocated_sales_point_id IS NULL")
                .contains("lf.sales_point_id = ds.allocated_sales_point_id")
                .contains("NVL(disposal.expected_disposal_qty, 0) AS expected_disposal_qty");
    }

    @Test
    void reservedQuantityIsZeroForInventoryThatCannotBeSold() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("ELSE cbb.reserved_qty")
                .contains("ELSE bb.reserved_qty")
                .contains("ELSE ib.reserved_qty")
                .contains("ELSE reserved_qty")
                .contains("SUM(CASE")
                .contains("AS reserved_quantity");
        assertThat(occurrences(inventorySql, "ELSE reserved_qty")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void channelPriceQueryUsesEveryPriceColumnWithoutCopyingSellingPrice() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("scp.actual_price")
                .contains("scp.minimum_selling_price")
                .doesNotContain("scp.selling_price AS actual_price")
                .doesNotContain("scp.selling_price AS minimum_selling_price");
    }

    @Test
    void integratedInventoryLocationsAreRestrictedToCenterOnlyStock() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("unassigned_balance_base")
                .contains("AND ib.allocated_sales_point_id IS NULL")
                .contains("LEFT JOIN risk_latest ur ON ur.sku_id = ua.sku_id AND ur.sales_point_id = -1")
                .contains("AND r.sales_point_id = -1")
                .contains("unassigned_current_qty")
                .contains("unassigned_shortage_yn")
                .contains("(SELECT r.shortage_yn FROM risk_agg r WHERE r.sku_id = ra.sku_id AND r.sales_point_id = -1) AS unassigned_shortage_yn")
                .contains("unassigned_risk_grade")
                .contains("unassigned_assessment_status")
                .contains("unassigned_risk_reason")
                .contains("unassigned_locations_json")
                .contains("'warehouseName' VALUE CAST(NULL AS VARCHAR2(200))")
                .contains("CASE WHEN #{salesPointCode} = 'UNASSIGNED'");
    }

    @Test
    void inventoryListAndDetailExposeTheProductSupplierName() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("sup.supplier_name AS supplier_name")
                .contains("LEFT JOIN supplier sup ON sup.supplier_id = p.supplier_id");
    }

    @Test
    void sellerRiskAggregationUsesTheLatestPersistedAssessmentPerScope() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("ORDER BY ra.updated_at DESC,", "ra.risk_assessment_id DESC")
                .doesNotContain("ORDER BY CASE ra.risk_grade")
                .contains("reason_message AS risk_reason")
                .contains("risk.risk_reason");
    }

    @Test
    void overallRiskFilterUsesTheSkuAggregateBeforeFilteringCandidates() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("sku_risk_agg AS")
                .contains("LEFT JOIN sku_risk_agg sr ON sr.sku_id = ib.sku_id")
                .contains("NVL(sr.risk_grade, 'UNASSESSED') =")
                .doesNotContain("NVL(r.risk_grade, 'UNASSESSED') =");
    }

    @Test
    void candidateFilterValuesUseTheValidatedAndOrOperator() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql)
                .contains("<sql id=\"candidateFilterOperator\">")
                .contains("<when test=\"filterOperator == 'OR'\">OR</when>")
                .contains("<sql id=\"candidateFilterWhere\">")
                .contains("<include refid=\"candidateFilterOperator\"/>")
                .contains("suffixOverrides=\"AND|OR\"")
                .doesNotContain("${filterOperator}");
    }

    @Test
    void inventoryDetailCteDoesNotLeaveADanglingDelimiterBeforeTheSelect() throws IOException {
        String inventorySql = read("inventory/InventoryMapper.xml");

        assertThat(inventorySql).doesNotContain("        ),\n        )\n        SELECT p.product_code");
    }

    @Test
    void centerOnlyPolicyIsNotAppliedToNamedSalesPoint() throws IOException {
        String riskSql = read("inventory/RiskAssessmentMapper.xml");

        assertThat(riskSql)
                .contains("#{salesPointCode} != 'UNASSIGNED' AND sp.sales_point_code = #{salesPointCode}")
                .contains("#{salesPointCode} = 'UNASSIGNED'")
                .contains("ip.allocated_sales_point_id IS NULL")
                .doesNotContain("ip.stock_sales_point_id IS NULL");
    }

    @Test
    void syncRiskSnapshotKeepsAllocatedNullAsUnassignedScope() throws IOException {
        String syncRiskSql = read("inventorysync/InventorySyncRiskSnapshotMapper.xml");

        assertThat(syncRiskSql)
                .contains("COALESCE(TO_CHAR(ib.allocated_sales_point_id), 'UNASSIGNED')")
                .contains("l.lot_status")
                .doesNotContain("COALESCE(TO_CHAR(COALESCE(ib.allocated_sales_point_id, ib.stock_sales_point_id))");
    }

    @Test
    void syncRiskSnapshotFindsScopesThatNeedTheCurrentRuleVersion() throws IOException {
        String syncRiskSql = read("inventorysync/InventorySyncRiskSnapshotMapper.xml");

        assertThat(syncRiskSql)
                .contains("selectScopesRequiringRuleVersion")
                .contains("LEFT JOIN risk_assessment ra")
                .contains("GROUP BY ib.sku_id, ib.allocated_sales_point_id")
                .contains("HAVING MAX(CASE WHEN ra.rule_version = #{ruleVersion} THEN 1 ELSE 0 END) = 0")
                .contains("COALESCE(TO_CHAR(ib.allocated_sales_point_id), 'UNASSIGNED') AS scope_key");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAPPER_ROOT.resolve(relativePath));
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
