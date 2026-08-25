package com.stockit.backend.feature.demandforecast.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;

@ExtendWith(MockitoExtension.class)
class DemandForecastModelVersionQueryServiceTest {

    @Mock
    private DemandForecastMapper demandForecastMapper;

    @InjectMocks
    private DemandForecastModelVersionQueryService queryService;

    @Test
    void delegatesModelIdentityLookupToDemandForecastMapper() {
        when(demandForecastMapper.selectModelVersionId(
                "stockit-demand-lightgbm", "3"
        )).thenReturn(81L);

        assertThat(queryService.findModelVersionId(
                "stockit-demand-lightgbm", "3"
        )).isEqualTo(81L);
        verify(demandForecastMapper).selectModelVersionId(
                "stockit-demand-lightgbm", "3"
        );
    }
}
