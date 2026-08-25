package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

@ExtendWith(MockitoExtension.class)
class ForecastModelVersionResolverTest {

    @Mock
    private DemandForecastMapper demandForecastMapper;

    private ForecastModelVersionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ForecastModelVersionResolver(demandForecastMapper);
    }

    @Test
    void resolvesExternalModelIdentityToInternalPrimaryKey() {
        when(demandForecastMapper.selectModelVersionId(
                "stockit-demand-lightgbm", "3"
        )).thenReturn(81L);

        assertThat(resolver.resolve(response())).isEqualTo(81L);
    }

    @Test
    void rejectsUnregisteredModelBeforeCheckpointIsSaved() {
        when(demandForecastMapper.selectModelVersionId(
                "stockit-demand-lightgbm", "3"
        )).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(response()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_MODEL_VERSION_NOT_REGISTERED")
                );
    }

    @Test
    void treatsDatabaseLookupFailureAsRetryable() {
        when(demandForecastMapper.selectModelVersionId(
                "stockit-demand-lightgbm", "3"
        )).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> resolver.resolve(response()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_MODEL_VERSION_LOOKUP_FAILED")
                );
    }

    private static StrategyForecastResponse response() {
        return new StrategyForecastResponse(
                1L,
                9281L,
                2L,
                List.of(2L),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 25),
                1,
                "coral_turnip_hlw05njfmc",
                "stockit-demand-lightgbm",
                "3",
                OffsetDateTime.parse("2026-08-25T00:09:29.446174Z"),
                List.of()
        );
    }
}
