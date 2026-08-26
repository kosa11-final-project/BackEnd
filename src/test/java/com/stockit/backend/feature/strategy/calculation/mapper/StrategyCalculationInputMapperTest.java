package com.stockit.backend.feature.strategy.calculation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/strategy-calculation-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyCalculationInputMapperTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:strategy-calculation;MODE=Oracle;"
                        + "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private StrategyCalculationInputMapper mapper;

    @Test
    void readsCalculationInputsAtEffectiveDateWithoutRecalculatingOnHand() {
        LocalDate asOfDate = LocalDate.of(2026, 8, 20);

        assertThat(mapper.selectActiveSku(101L)).satisfies(sku -> {
            assertThat(sku.getSkuCode()).isEqualTo("SKU-101");
            assertThat(sku.getPackageQuantity()).isEqualByComparingTo("1");
            assertThat(sku.getNetWeight()).isEqualByComparingTo("0.5");
            assertThat(sku.getWeightUnit()).isEqualTo("KG");
        });
        assertThat(mapper.selectInventory(101L)).singleElement().satisfies(inventory -> {
            assertThat(inventory.getLotId()).isEqualTo(1001L);
            assertThat(inventory.getOnHandQty()).isEqualByComparingTo("20");
            assertThat(inventory.getReservedQty()).isEqualByComparingTo("3");
            assertThat(inventory.getExpiryDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        });
        assertThat(mapper.selectActiveSalesPoints(List.of(10L, 20L)))
                .extracting(point -> point.getSalesPointId())
                .containsExactly(10L, 20L);
        assertThat(mapper.selectActiveWarehouseRoutes(List.of(10L, 20L)))
                .extracting(route -> route.getWarehouseId())
                .containsExactly(501L, 502L, 501L);
        assertThat(mapper.selectEffectivePrices(101L, List.of(10L, 20L), asOfDate))
                .singleElement()
                .satisfies(price -> {
                    assertThat(price.getSalesPointId()).isEqualTo(10L);
                    assertThat(price.getPaymentFee()).isEqualByComparingTo("300");
                    assertThat(price.getLogisticsCost()).isEqualByComparingTo("500");
                });
        assertThat(mapper.selectEffectiveCosts(101L, asOfDate))
                .singleElement()
                .satisfies(cost -> assertThat(cost.getUnitCost())
                        .isEqualByComparingTo("6000"));
        assertThat(mapper.selectEffectivePolicies(101L, asOfDate))
                .singleElement()
                .satisfies(policy -> {
                    assertThat(policy.getSafetyStockQty()).isEqualByComparingTo("5");
                    assertThat(policy.getDailyUnitHoldingCost())
                            .isEqualByComparingTo("2.5");
                });
        assertThat(mapper.selectActiveTransferRoutes(
                List.of(List.of(501L)),
                List.of(),
                List.of(List.of(502L)),
                List.of(List.of(20L))
        ))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getSourceWarehouseId()).isEqualTo(501L);
                    assertThat(route.getDestinationSalesPointId()).isEqualTo(20L);
                    assertThat(route.getDistanceKm()).isEqualByComparingTo("25.5");
                });
        assertThat(mapper.selectActiveTransferRoutes(
                List.of(List.of(999L)),
                List.of(),
                List.of(List.of(998L)),
                List.of()
        )).isEmpty();
        assertThat(mapper.selectTransferCostPolicies(
                asOfDate, asOfDate.plusDays(90)
        ))
                .singleElement()
                .satisfies(policy -> assertThat(policy.getCostPerKgKm())
                        .isEqualByComparingTo("2"));
    }

    @Test
    void readsTransferRouteWhenDestinationIdsSpanMultipleOracleInChunks() {
        List<Long> destinationIds = LongStream.rangeClosed(1, 1001)
                .boxed()
                .toList();
        List<List<Long>> destinationIdChunks = List.of(
                destinationIds.subList(0, 900),
                destinationIds.subList(900, destinationIds.size())
        );

        assertThat(mapper.selectActiveTransferRoutes(
                List.of(List.of(501L)),
                List.of(),
                List.of(),
                destinationIdChunks
        )).singleElement().satisfies(route ->
                assertThat(route.getDestinationSalesPointId()).isEqualTo(20L)
        );
    }

    @Test
    void includesPricesWhenEffectiveDateEqualsEitherBoundary() {
        LocalDate asOfDate = LocalDate.of(2026, 8, 20);

        assertThat(mapper.selectEffectivePrices(
                101L,
                List.of(30L, 40L),
                asOfDate
        )).extracting(price -> price.getSalesPointId())
                .containsExactly(30L, 40L);
    }
}
