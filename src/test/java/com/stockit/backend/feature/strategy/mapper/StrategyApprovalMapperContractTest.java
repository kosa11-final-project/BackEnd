package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StrategyApprovalMapperContractTest {

    private static final Path MAPPER = Path.of(
            "src/main/resources/mappers/strategy/StrategyApprovalMapper.xml"
    );

    @Test
    void nullableApprovalWritesUseExplicitOracleJdbcTypes() throws Exception {
        String sql = Files.readString(MAPPER);

        assertThat(sql)
                .contains("#{strategyPrice,jdbcType=NUMERIC}")
                .contains("#{movementCost,jdbcType=NUMERIC}")
                .contains("#{unitContributionMargin,jdbcType=NUMERIC}")
                .contains("#{expectedSellThroughDays,jdbcType=NUMERIC}")
                .contains("#{sourceSalesPointId,jdbcType=NUMERIC}")
                .contains("#{targetSalesPointId,jdbcType=NUMERIC}")
                .contains("#{sourceWarehouseId,jdbcType=NUMERIC}")
                .contains("#{destinationWarehouseId,jdbcType=NUMERIC}")
                .contains("#{discountRate,jdbcType=NUMERIC}")
                .contains("#{startDate,jdbcType=DATE}")
                .contains("#{endDate,jdbcType=DATE}")
                .contains("#{salesPointId,jdbcType=NUMERIC}")
                .contains("#{dailySalesVelocity,jdbcType=NUMERIC}")
                .contains("#{forecastQty,jdbcType=NUMERIC}")
                .contains("#{expiryDate,jdbcType=DATE}")
                .contains("#{forecast30dQty,jdbcType=NUMERIC}")
                .contains("#{forecast180dQty,jdbcType=NUMERIC}")
                .contains("#{dailyForecastJson,jdbcType=CLOB}")
                .contains("#{forecastGeneratedAt,jdbcType=TIMESTAMP}")
                .contains("#{teamsMessageId,jdbcType=VARCHAR}");
    }

    @Test
    void priceSnapshotDoesNotInsertOracleVirtualContributionMargin() throws Exception {
        String sql = Files.readString(MAPPER);
        String priceSnapshotInsert = sql.substring(
                sql.indexOf("<insert id=\"insertPriceSnapshot\">"),
                sql.indexOf("</insert>", sql.indexOf(
                        "<insert id=\"insertPriceSnapshot\">"
                ))
        );

        assertThat(priceSnapshotInsert)
                .contains("unit_variable_cost")
                .doesNotContain("baseline_unit_contribution_margin")
                .doesNotContain("baselineUnitContributionMargin");
    }
}
