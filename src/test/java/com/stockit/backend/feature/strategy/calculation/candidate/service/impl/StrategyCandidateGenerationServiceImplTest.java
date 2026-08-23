package com.stockit.backend.feature.strategy.calculation.candidate.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.candidate.calculator.StrategyCandidateCalculator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyType;

@ExtendWith(MockitoExtension.class)
class StrategyCandidateGenerationServiceImplTest {

    @Mock
    private StrategyCandidateCalculator reallocationCalculator;
    @Mock
    private StrategyCandidateCalculator transferCalculator;

    @Test
    void preservesOriginalUserTypePriorityWhenEarlierTypeIsNotYetImplemented() {
        when(reallocationCalculator.supportedType()).thenReturn(StrategyType.REALLOCATION);
        when(transferCalculator.supportedType()).thenReturn(StrategyType.RT_TRANSFER);
        StrategyCandidateGenerationServiceImpl service =
                new StrategyCandidateGenerationServiceImpl(List.of(
                        reallocationCalculator,
                        transferCalculator
                ));
        StrategyCalculationContext context = context(List.of(
                StrategyType.PRICE_DISCOUNT,
                StrategyType.RT_TRANSFER
        ));
        when(transferCalculator.generate(context, 2)).thenReturn(
                new CandidateGenerationResult(List.of(), List.of())
        );

        CandidateGenerationResult result = service.generate(context);

        assertThat(result.candidates()).isEmpty();
        verify(transferCalculator).generate(context, 2);
        verify(reallocationCalculator, never()).generate(context, 1);
    }

    private static StrategyCalculationContext context(List<StrategyType> types) {
        StrategyCalculationContext.InventoryLot inventory =
                new StrategyCalculationContext.InventoryLot(
                        1L,
                        1001L,
                        501L,
                        10L,
                        10L,
                        decimal("10"),
                        BigDecimal.ZERO,
                        null,
                        LocalDate.of(2026, 8, 1),
                        null,
                        null,
                        "AVAILABLE"
                );
        return new StrategyCalculationContext(
                12345L,
                10L,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21),
                new StrategyCalculationContext.Sku(
                        101L, "SKU-101", "테스트 SKU", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L),
                        types,
                        null,
                        null
                ),
                List.of(inventory),
                List.of(inventory),
                List.of(),
                new LinkedHashMap<>(),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-run-1",
                        3L,
                        OffsetDateTime.of(
                                2026, 8, 20, 9, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
