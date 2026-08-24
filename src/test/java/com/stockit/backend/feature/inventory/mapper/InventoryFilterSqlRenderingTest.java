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

    private String render(String statement, InventoryQuery query) {
        BoundSql boundSql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(MAPPER_NAMESPACE + "." + statement)
                .getBoundSql(query);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
