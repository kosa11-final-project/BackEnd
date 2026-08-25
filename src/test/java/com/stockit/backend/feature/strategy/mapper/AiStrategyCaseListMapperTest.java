package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseStatusCountVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/ai-strategy-case-list-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class AiStrategyCaseListMapperTest {

    private static final LocalDateTime VISIBLE_AT = LocalDateTime.of(2026, 8, 24, 10, 0);
    private static final LocalDateTime VISIBLE_FROM = LocalDateTime.of(2026, 8, 21, 10, 0);

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:ai-strategy-list;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private AiStrategyCaseListMapper mapper;

    @Test
    void listsOnlyUnselectedCasesStillInsideThreeDayVisibilityWindow() {
        AiStrategyCaseListQuery query = query(0, 10, null, null, null, null, null, "DESC");

        List<AiStrategyCaseListVO> cases = mapper.selectCases(
                query, VISIBLE_AT, VISIBLE_FROM
        );

        assertThat(mapper.countCases(query, VISIBLE_AT, VISIBLE_FROM)).isEqualTo(3);
        assertThat(cases).extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(102L, 101L, 104L);
        assertThat(cases.get(0)).satisfies(value -> {
            assertThat(value.getSkuCode()).isEqualTo("SKU_TOFU");
            assertThat(value.getImageUrl()).isEqualTo("https://example.com/tofu.jpg");
            assertThat(value.getCategoryName()).isEqualTo("가공식품");
            assertThat(value.getRequesterName()).isEqualTo("이주영");
        });
    }

    @Test
    void excludesGeneratingAndFailedCasesAtExactThreeDayBoundary() {
        AiStrategyCaseListQuery query = query(
                0, 10, "정확히 3일", null, null, null, null, "DESC"
        );

        assertThat(mapper.countCases(query, VISIBLE_AT, VISIBLE_FROM)).isZero();
        assertThat(mapper.selectCases(query, VISIBLE_AT, VISIBLE_FROM)).isEmpty();
    }

    @Test
    void appliesStatusOnlyToPageWhileCountsUseSearchAndDateConditions() {
        AiStrategyCaseListQuery query = query(
                0, 10, null, null, "GENERATED",
                LocalDateTime.of(2026, 8, 22, 0, 0),
                LocalDateTime.of(2026, 8, 25, 0, 0),
                "DESC"
        );

        assertThat(mapper.selectCases(query, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(102L);
        assertThat(mapper.countCasesByStatus(query, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseStatusCountVO::getCaseStatus)
                .containsExactlyInAnyOrder(
                        com.stockit.backend.feature.strategy.domain.StrategyCaseStatus.GENERATING,
                        com.stockit.backend.feature.strategy.domain.StrategyCaseStatus.GENERATED,
                        com.stockit.backend.feature.strategy.domain.StrategyCaseStatus.GENERATION_FAILED
                );
    }

    @Test
    void searchesNumericCaseIdExactlyAndTextFieldsLiterally() {
        AiStrategyCaseListQuery byId = query(0, 10, "102", 102L, null, null, null, "DESC");
        assertThat(mapper.selectCases(byId, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(102L);

        AiStrategyCaseListQuery byProduct = query(0, 10, "국산콩", null, null, null, null, "DESC");
        assertThat(mapper.selectCases(byProduct, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(102L, 101L);

        AiStrategyCaseListQuery literalWildcard = query(
                0, 10, "SKU\\_TOFU", null, null, null, null, "DESC"
        );
        assertThat(mapper.selectCases(literalWildcard, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(102L, 101L);
    }

    @Test
    void appliesStablePaginationAndAscendingSort() {
        AiStrategyCaseListQuery first = query(0, 1, null, null, null, null, null, "ASC");
        AiStrategyCaseListQuery second = query(1, 1, null, null, null, null, null, "ASC");

        assertThat(mapper.selectCases(first, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(104L);
        assertThat(mapper.selectCases(second, VISIBLE_AT, VISIBLE_FROM))
                .extracting(AiStrategyCaseListVO::getStrategyCaseId)
                .containsExactly(101L);
    }

    private static AiStrategyCaseListQuery query(
            int page,
            int size,
            String searchText,
            Long strategyCaseId,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            String direction
    ) {
        return new AiStrategyCaseListQuery(
                page, size, searchText, strategyCaseId, status, from, to, direction
        );
    }
}
