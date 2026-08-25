package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

class StrategyPeriodEligibilityPolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 10);

    private final StrategyPeriodEligibilityPolicy policy =
            new StrategyPeriodEligibilityPolicy();

    @Test
    void usesLatestSellableEndAcrossAllocatedLots() {
        StrategyCalculationContext context = context(List.of(
                lot(1L, 101L, LocalDate.of(2026, 9, 4), null),
                lot(2L, 102L, LocalDate.of(2026, 9, 9), null)
        ));

        LocalDate latest = policy.latestSelectableEndDate(
                context,
                List.of(1L, 2L)
        );

        assertThat(latest).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void limitsAdjustedPeriodAgainUsingOnlyActuallyAllocatedLots() {
        StrategyCalculationContext context = context(List.of(
                lot(1L, 101L, LocalDate.of(2026, 9, 4), null),
                lot(2L, 102L, LocalDate.of(2026, 9, 9), null)
        ));

        assertThatThrownBy(() -> policy.validateAllocatedPeriod(
                context,
                LocalDate.of(2026, 9, 7),
                List.of(1L)
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_SELLABLE_END_EXCEEDED)
        );
    }

    @Test
    void treatsExpiryAsInclusiveAndSaleStopAsExclusive() {
        StrategyCalculationContext.InventoryLot lot = lot(
                1L,
                101L,
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 6)
        );

        assertThat(policy.sellableEndDate(lot))
                .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void unboundedAllocatedLotAllowsForecastEndDate() {
        StrategyCalculationContext context = context(List.of(
                lot(1L, 101L, LocalDate.of(2026, 9, 4), null),
                lot(2L, 102L, null, null)
        ));

        assertThat(policy.latestSelectableEndDate(context, List.of(1L, 2L)))
                .isEqualTo(END);
    }

    @Test
    void rejectsRecommendationStartThatIsBeforeBusinessDate() {
        StrategyCalculationContext context = context(List.of(
                lot(1L, 101L, null, null)
        ));

        assertThatThrownBy(() -> policy.validateRequestedPeriod(
                context,
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 3)
        )).isInstanceOfSatisfying(
                AppException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_PERIOD_STALE)
        );
    }

    private static StrategyCalculationContext context(
            List<StrategyCalculationContext.InventoryLot> lots
    ) {
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
            forecasts.put(date, BigDecimal.ONE);
        }
        StrategyCalculationContext.Price price =
                new StrategyCalculationContext.Price(
                        1L,
                        decimal("120"),
                        decimal("100"),
                        decimal("70"),
                        decimal("5"),
                        decimal("10")
                );
        StrategyCalculationContext.SalesPoint salesPoint =
                new StrategyCalculationContext.SalesPoint(
                        10L,
                        "DEPT_PANGYO",
                        "판교점",
                        BigDecimal.ZERO,
                        true,
                        price,
                        forecasts,
                        List.of()
                );
        return new StrategyCalculationContext(
                1L,
                10L,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                START,
                END,
                new StrategyCalculationContext.Sku(
                        100L, "SKU-100", "상품", "EA", BigDecimal.ONE
                ),
                decimal("70"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                lots,
                lots,
                List.of(),
                Map.of(10L, salesPoint),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-1",
                        1L,
                        OffsetDateTime.of(
                                2026, 9, 1, 8, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static StrategyCalculationContext.InventoryLot lot(
            Long inventoryBalanceId,
            Long lotId,
            LocalDate expiryDate,
            LocalDate saleStopDate
    ) {
        return new StrategyCalculationContext.InventoryLot(
                inventoryBalanceId,
                lotId,
                501L,
                10L,
                10L,
                decimal("10"),
                BigDecimal.ZERO,
                null,
                START.minusDays(10),
                expiryDate,
                saleStopDate,
                "AVAILABLE"
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
