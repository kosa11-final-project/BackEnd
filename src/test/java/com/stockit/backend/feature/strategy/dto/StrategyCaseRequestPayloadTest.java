package com.stockit.backend.feature.strategy.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.domain.StrategyType;

class StrategyCaseRequestPayloadTest {

    @Test
    void keepsListSnapshotImmutableAfterConstruction() {
        List<Long> lotIds = new ArrayList<>(List.of(1001L));
        List<Long> candidateSalesPointIds = new ArrayList<>(List.of(20L));
        List<StrategyType> strategyTypes =
                new ArrayList<>(List.of(StrategyType.PRICE_DISCOUNT));

        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                lotIds,
                candidateSalesPointIds,
                strategyTypes,
                null,
                null,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 11, 17)
        );

        lotIds.add(1002L);
        candidateSalesPointIds.add(30L);
        strategyTypes.add(StrategyType.CHANNEL_EXPANSION);

        assertThat(payload.lotIds()).containsExactly(1001L);
        assertThat(payload.candidateSalesPointIds()).containsExactly(20L);
        assertThat(payload.strategyTypes()).containsExactly(StrategyType.PRICE_DISCOUNT);
        assertThatThrownBy(() -> payload.lotIds().add(1003L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
