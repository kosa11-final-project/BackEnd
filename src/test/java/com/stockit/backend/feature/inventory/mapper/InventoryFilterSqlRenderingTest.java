package com.stockit.backend.feature.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.stockit.backend.feature.inventory.dto.request.InventoryQueryRequest;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;

@SpringBootTest
@ActiveProfiles("test")
class InventoryFilterSqlRenderingTest {

    private static final String MAPPER_NAMESPACE = InventoryMapper.class.getName();

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void rendersTheValidatedOrOperatorForListCountAndSummary() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setQ("만두");
        request.setStorageType(List.of("FROZEN"));
        request.setRiskGrade(List.of("DANGER"));
        request.setFilterOperator("OR");
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("OR s.storage_type =")
                    .contains("OR NVL(sr.risk_grade, 'UNASSESSED') =")
                    .doesNotContain("${filterOperator}");
        }
    }

    @Test
    void rendersEverySupportedFilterForListCountAndSummary() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setQ("만두");
        request.setChannelType(List.of("GREETING"));
        request.setSalesPointCode(List.of("GREETING"));
        request.setWarehouseCode(List.of("GYEONGIN_1"));
        request.setRegionCode(List.of("GYEONGGI"));
        request.setCategoryId("301");
        request.setStorageType(List.of("FROZEN"));
        request.setRiskGrade(List.of("NORMAL"));
        request.setAssessmentStatus(List.of("ASSESSED"));
        request.setShortageYn("Y");
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("LOWER(p.product_name) LIKE")
                    .contains("filter_sp.sales_point_code =")
                    .contains("filter_region_sp.region_code =")
                    .contains("START WITH category_id =")
                    .contains("s.storage_type =")
                    .contains("filtered_w.warehouse_code =")
                    .contains("NVL(sr.risk_grade, 'UNASSESSED') =")
                    .contains("NVL(sr.assessment_status, 'UNASSESSED') =")
                    .contains("NVL(sr.shortage_yn, 'N') = ?");
        }
    }

    @Test
    void appliesOrToEverySelectedFilterValueInListCountAndSummary() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setQ("만두");
        request.setChannelType(List.of("GREETING"));
        request.setSalesPointCode(List.of("GREETING"));
        request.setWarehouseCode(List.of("GYEONGIN_1"));
        request.setRegionCode(List.of("GYEONGGI"));
        request.setCategoryIds(List.of("301", "302"));
        request.setStorageType(List.of("FROZEN", "ROOM_TEMP"));
        request.setRiskGrade(List.of("NORMAL", "DANGER"));
        request.setAssessmentStatus(List.of("ASSESSED"));
        request.setShortageYn("Y");
        request.setFilterOperator("OR");
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("OR LOWER(p.product_name) LIKE")
                    .contains("OR EXISTS ( SELECT 1 FROM inventory_balance channel_ib")
                    .contains("OR EXISTS ( SELECT 1 FROM inventory_balance filter_sp_ib")
                    .contains("OR EXISTS ( SELECT 1 FROM inventory_balance filter_region_ib")
                    .contains("OR p.category_id IN")
                    .contains("OR s.storage_type IN")
                    .contains("OR EXISTS ( SELECT 1 FROM inventory_balance filtered_ib")
                    .contains("OR NVL(sr.risk_grade, 'UNASSESSED') =")
                    .contains("OR NVL(sr.assessment_status, 'UNASSESSED') =")
                    .contains("OR NVL(sr.shortage_yn, 'N') = ?");
        }
    }

    @Test
    void appliesAndToEverySelectedFilterValueInListCountAndSummary() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setQ("만두");
        request.setChannelType(List.of("GREETING", "HMART"));
        request.setSalesPointCode(List.of("STORE_A", "STORE_B"));
        request.setWarehouseCode(List.of("GYEONGIN_1", "GYEONGIN_2"));
        request.setRegionCode(List.of("GYEONGGI", "SEOUL"));
        request.setCategoryIds(List.of("301", "302"));
        request.setStorageType(List.of("FROZEN", "ROOM_TEMP"));
        request.setRiskGrade(List.of("NORMAL", "DANGER"));
        request.setAssessmentStatus(List.of("ASSESSED", "UNASSESSED"));
        request.setShortageYn("Y");
        request.setFilterOperator("AND");
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("AND ( SELECT COUNT(DISTINCT CASE")
                    .contains("AND ( SELECT COUNT(DISTINCT filter_sp.sales_point_code)")
                    .contains("AND ( SELECT COUNT(DISTINCT filter_region_sp.region_code)")
                    .contains("AND p.category_id IN")
                    .contains("AND s.storage_type =")
                    .contains("AND ( SELECT COUNT(DISTINCT filtered_w.warehouse_code)")
                    .contains("AND NVL(sr.risk_grade, 'UNASSESSED') =")
                    .contains("AND NVL(sr.assessment_status, 'UNASSESSED') =")
                    .contains("AND NVL(sr.shortage_yn, 'N') = ?");
        }
    }

    @Test
    void appliesTheSelectedOperatorBetweenMultipleCategoryValues() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setCategoryIds(List.of("301", "302"));
        request.setFilterOperator("AND");
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql).as(statement)
                    .contains("START WITH category_id = ?")
                    .contains("CONNECT BY NOCYCLE PRIOR category_id = parent_category_id")
                    .contains(") AND p.category_id IN ( SELECT descendant.category_id");
            assertThat(occurrences(sql, "START WITH category_id = ?")).isEqualTo(2);
        }
    }

    @Test
    void appliesTheSelectedOperatorBetweenMultipleChannelValues() {
        InventoryQueryRequest andRequest = new InventoryQueryRequest();
        andRequest.setChannelType(List.of("ECOMMERCE", "GREETING", "HMART"));
        andRequest.setFilterOperator("AND");
        InventoryQuery andQuery = andRequest.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, andQuery);

            assertThat(sql)
                    .as(statement)
                    .contains("COUNT(DISTINCT CASE")
                    .contains("= ?");
            assertThat(occurrences(sql, "FROM inventory_balance channel_ib")).isEqualTo(1);
        }

        InventoryQueryRequest orRequest = new InventoryQueryRequest();
        orRequest.setChannelType(List.of("ECOMMERCE", "GREETING", "HMART"));
        orRequest.setFilterOperator("OR");

        String orSql = render("selectInventoryList", orRequest.toQuery(LocalDate.of(2026, 8, 24)));
        assertThat(orSql).contains("EXISTS ( SELECT 1 FROM inventory_balance channel_ib");
        assertThat(occurrences(orSql, "FROM inventory_balance channel_ib")).isEqualTo(1);
    }

    @Test
    void returnsTheTotalCountFromThePagedListQuery() {
        String sql = render(
                "selectInventoryList",
                new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 24))
        );

        assertThat(sql)
                .contains("COUNT(*) OVER () AS total_count")
                .contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
    }

    @Test
    void usesTheLatestAssessmentAndLatestPolicyForInventoryQueries() {
        InventoryQuery query = new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("ORDER BY ra.updated_at DESC, ra.risk_assessment_id DESC")
                    .doesNotContain("ORDER BY CASE ra.risk_grade");
        }

        assertThat(render("selectInventoryList", query))
                .contains("ORDER BY effective_from DESC NULLS LAST, inventory_policy_id DESC");
        assertThat(render("selectInventorySummary", query))
                .contains("ORDER BY effective_from DESC NULLS LAST, inventory_policy_id DESC")
                .contains("ra.shortage_yn")
                .contains("CASE WHEN ls.shortage_yn = 'Y' THEN 1 ELSE 0 END")
                .contains("CASE WHEN MAX(sf.shortage_count) = 1 THEN 1 ELSE 0 END")
                .contains("/*+ MATERIALIZE */")
                .contains("PARTITION BY ib.inventory_balance_id");

        assertThat(occurrences(render("selectInventorySummary", query), "FROM risk_assessment ra"))
                .isEqualTo(1);
    }

    @Test
    void rendersEveryWhitelistedSortWithNullsLastAndAStableSkuTieBreaker() {
        for (var sort : List.of(
                new String[]{"updatedAt,desc", "q.updated_at", "DESC"},
                new String[]{"productName,asc", "q.product_name", "ASC"},
                new String[]{"skuCode,desc", "q.sku_code", "DESC"},
                new String[]{"currentQuantity,asc", "q.current_qty", "ASC"},
                new String[]{"availableQuantity,desc", "q.available_qty", "DESC"},
                new String[]{"reservedQuantity,asc", "q.reserved_qty", "ASC"},
                new String[]{"expectedDisposalQuantity,desc", "q.expected_disposal_qty", "DESC"},
                new String[]{"nearestExpiryDays,asc", "q.nearest_expiry_days", "ASC"}
        )) {
            InventoryQueryRequest request = new InventoryQueryRequest();
            request.setSort(sort[0]);
            String sql = render("selectInventoryList", request.toQuery(LocalDate.of(2026, 8, 24)));

            assertThat(sql)
                    .contains("ORDER BY CASE WHEN " + sort[1] + " IS NULL THEN 1 ELSE 0 END, "
                            + sort[1] + " " + sort[2] + ", q.sku_code ASC");
        }

        InventoryQueryRequest riskRequest = new InventoryQueryRequest();
        riskRequest.setSort("riskGrade,desc");
        assertThat(render("selectInventoryList", riskRequest.toQuery(LocalDate.of(2026, 8, 24))))
                .contains("ORDER BY CASE WHEN q.risk_grade IS NULL THEN 1 ELSE 0 END")
                .contains("END DESC, q.sku_code ASC");
    }

    @Test
    void calculatesExpectedDisposalBeforePaginationSoSortingCoversTheWholeResultSet() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setSort("expectedDisposalQuantity,desc");

        String sql = render("selectInventoryList", request.toQuery(LocalDate.of(2026, 8, 24)));

        assertThat(sql)
                .contains("NVL(db.expected_disposal_qty, 0) AS expected_disposal_qty")
                .contains("ORDER BY CASE WHEN q.expected_disposal_qty IS NULL THEN 1 ELSE 0 END, "
                        + "q.expected_disposal_qty DESC, q.sku_code ASC");
        assertThat(sql.indexOf("disposal_by_sku AS")).isLessThan(sql.indexOf("paged_candidates AS"));
    }

    private String render(String statement, InventoryQuery query) {
        BoundSql boundSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(MAPPER_NAMESPACE + "." + statement)
                .getBoundSql(query);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
