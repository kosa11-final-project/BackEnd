package com.stockit.backend.feature.demandforecast.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/demand-forecast-import-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DemandForecastMapperTest {

    @Autowired
    private DemandForecastMapper demandForecastMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resolvesModelAndValidatesReferenceIdsInBulk() {
        assertThat(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .isEqualTo(7L);
        assertThat(demandForecastMapper.countExistingSkus(List.of(101L, 102L)))
                .isEqualTo(2);
        assertThat(demandForecastMapper.countExistingSalesPoints(List.of(10L)))
                .isEqualTo(1);
    }

    @Test
    void insertsThenUpdatesByForecastUniqueTarget() {
        DemandForecastVO initial = forecast(new BigDecimal("12.300"), "HIGH");
        demandForecastMapper.mergeDemandForecasts(List.of(initial));

        DemandForecastVO updated = forecast(new BigDecimal("13.500"), "MEDIUM");
        updated.setPredictedQtyD14(new BigDecimal("25.500"));
        demandForecastMapper.mergeDemandForecasts(List.of(updated));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demand_forecast",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT predicted_qty_d7 FROM demand_forecast",
                BigDecimal.class
        )).isEqualByComparingTo("13.500");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confidence_level FROM demand_forecast",
                String.class
        )).isEqualTo("MEDIUM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_by FROM demand_forecast",
                Long.class
        )).isEqualTo(99L);
    }

    private static DemandForecastVO forecast(BigDecimal d7, String confidenceLevel) {
        return DemandForecastVO.forImport(
                101L,
                10L,
                7L,
                LocalDate.of(2026, 7, 31),
                d7,
                new BigDecimal("24.800"),
                new BigDecimal("51.200"),
                new BigDecimal("103.700"),
                new BigDecimal("157.100"),
                "LIGHTGBM",
                confidenceLevel,
                99L
        );
    }
}
