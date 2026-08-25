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
                    .contains("OR s.storage_type IN")
                    .contains("OR NVL(sr.risk_grade, 'UNASSESSED') IN")
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
        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 24));

        for (String statement : List.of("selectInventoryList", "countInventory", "selectInventorySummary")) {
            String sql = render(statement, query);

            assertThat(sql)
                    .as(statement)
                    .contains("LOWER(p.product_name) LIKE")
                    .contains("sp.sales_point_code = 'GREETING'")
                    .contains("sp.sales_point_code IN")
                    .contains("sp.region_code IN")
                    .contains("START WITH category_id = ?")
                    .contains("s.storage_type IN")
                    .contains("filtered_w.warehouse_code IN")
                    .contains("NVL(sr.risk_grade, 'UNASSESSED') IN")
                    .contains("NVL(sr.assessment_status, 'UNASSESSED') IN");
        }
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

    private String render(String statement, InventoryQuery query) {
        BoundSql boundSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(MAPPER_NAMESPACE + "." + statement)
                .getBoundSql(query);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
