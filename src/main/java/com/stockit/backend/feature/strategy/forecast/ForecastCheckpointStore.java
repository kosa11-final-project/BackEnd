package com.stockit.backend.feature.strategy.forecast;

import java.util.Optional;

public interface ForecastCheckpointStore {

    Optional<ForecastCheckpoint> find(
            Long strategyCaseId,
            String expectedRequestHash,
            java.util.List<Long> expectedSalesPointIds
    );

    void save(ForecastCheckpoint checkpoint);
}
