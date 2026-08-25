package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/**
 * Oracle에서 nullable NUMBER 파라미터의 실제 저장 계약을 검증하는 선택 실행 테스트
 */
@SpringBootTest(properties = "app.ai-strategy.messaging.enabled=false")
@EnabledIfEnvironmentVariable(named = "STRATEGY_CASE_ORACLE_TEST", matches = "true")
@Transactional
class StrategyCaseOracleMapperTest {

    @Autowired
    private StrategyCaseMapper strategyCaseMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.hikari.schema", () -> "KOSA");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void insertsAndReadsCaseWithoutRequestedSalesPoint() {
        Long skuId = jdbcTemplate.queryForObject(
                """
                SELECT MIN(sku_id)
                FROM sku
                WHERE active_yn = 'Y'
                  AND is_deleted = 0
                """,
                Long.class
        );
        Long requesterId = jdbcTemplate.queryForObject(
                """
                SELECT MIN(user_id)
                FROM app_user
                WHERE active_yn = 'Y'
                  AND is_deleted = 0
                """,
                Long.class
        );
        assertThat(skuId).isNotNull();
        assertThat(requesterId).isNotNull();

        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                skuId,
                null,
                "SC-" + UUID.randomUUID().toString().replace("-", ""),
                "Oracle nullable 판매처 계약 테스트",
                "{\"lotIds\":[]}",
                requesterId
        );

        strategyCaseMapper.insertStrategyCase(strategyCase);

        StrategyCaseVO saved = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        assertThat(saved).isNotNull();
        assertThat(saved.getRequestedSalesPointId()).isNull();
    }
}
