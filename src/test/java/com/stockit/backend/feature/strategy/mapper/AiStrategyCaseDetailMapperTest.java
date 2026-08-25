package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/ai-strategy-case-detail-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class AiStrategyCaseDetailMapperTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:ai-strategy-detail;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private AiStrategyCaseDetailMapper mapper;

    @Test
    void loadsCaseHeaderAndDisplayReferencesWithoutChangingStoredResult() {
        assertThat(mapper.selectCaseDetail(123L)).satisfies(detail -> {
            assertThat(detail.getCaseStatus()).isEqualTo(StrategyCaseStatus.GENERATED);
            assertThat(detail.getSkuCode()).isEqualTo("GF-SOUP-MSH-06");
            assertThat(detail.getImageUrl())
                    .isEqualTo("https://example.com/mushroom-soup.jpg");
            assertThat(detail.getCategoryName()).isEqualTo("국·탕");
            assertThat(detail.getRequesterName()).isEqualTo("김영만");
            assertThat(detail.getRequestedSalesPointId()).isEqualTo(10L);
            assertThat(detail.getRequestPayloadJson()).contains("RT_TRANSFER");
        });

        assertThat(mapper.selectSalesPoints(List.of(10L, 20L)))
                .extracting(value -> value.getSalesPointCode())
                .containsExactlyInAnyOrder("DEPT_MOKDONG", "DEPT_PANGYO");
        assertThat(mapper.selectWarehouses(List.of(500L, 600L)))
                .extracting(value -> value.getWarehouseName())
                .containsExactlyInAnyOrder("경인센터", "수지센터");
        assertThat(mapper.selectLots(List.of(501L, 502L)))
                .extracting(value -> value.getLotCode())
                .containsExactlyInAnyOrder("LOT-260801-A", "LOT-260802-B");
    }
}
