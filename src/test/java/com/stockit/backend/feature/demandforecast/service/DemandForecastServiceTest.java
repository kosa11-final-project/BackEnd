package com.stockit.backend.feature.demandforecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportItemRequest;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastPayloadHash;
import com.stockit.backend.feature.demandforecast.service.impl.DemandForecastServiceImpl;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastStagingVO;

@ExtendWith(MockitoExtension.class)
class DemandForecastServiceTest {

    @Mock
    private DemandForecastMapper demandForecastMapper;

    private DemandForecastService demandForecastService;

    @BeforeEach
    void setUp() {
        demandForecastService = new DemandForecastServiceImpl(demandForecastMapper);
        DemandForecastRunVO run = new DemandForecastRunVO();
        run.setForecastRunId(501L);
        run.setAzureJobId("purple_monkey_gyk4m5yyxr");
        run.setBaseDate(LocalDate.of(2026, 7, 31));
        run.setRunStatus("RUNNING");
        lenient().when(demandForecastMapper.selectRunByAzureJobIdForUpdate(
                "purple_monkey_gyk4m5yyxr"
        )).thenReturn(run);
        lenient().when(demandForecastMapper.initializeImportManifest(
                501L, 7L, LocalDate.of(2026, 7, 31), 10, 10L, 99L
        )).thenReturn(1);
        lenient().when(demandForecastMapper.updateImportProgress(501L, 1, 2L, 10L, 99L))
                .thenReturn(1);
    }

    @Test
    void importsEachBatchWithinOneTransaction() throws NoSuchMethodException {
        Transactional transactional = DemandForecastServiceImpl.class
                .getMethod("importForecasts", DemandForecastImportRequest.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void validatesReferencesInBulkAndStagesTheBatch() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L), item(102L, 10L)));
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);
        when(demandForecastMapper.countExistingSkus(anyList())).thenReturn(2);
        when(demandForecastMapper.countExistingSalesPoints(anyList())).thenReturn(1);
        when(demandForecastMapper.countReceivedBatches(501L)).thenReturn(1);
        when(demandForecastMapper.sumReceivedItems(501L)).thenReturn(2L);

        DemandForecastImportResponse response = demandForecastService.importForecasts(request, 99L);

        assertThat(response.modelVersionId()).isEqualTo(7L);
        assertThat(response.importedCount()).isEqualTo(2);
        ArgumentCaptor<List<DemandForecastStagingVO>> forecasts = ArgumentCaptor.forClass(List.class);
        verify(demandForecastMapper).insertStagingForecasts(forecasts.capture());
        assertThat(forecasts.getValue())
                .hasSize(2)
                .allSatisfy(forecast -> {
                    assertThat(forecast.getForecastRunId()).isEqualTo(501L);
                    assertThat(forecast.getBatchNumber()).isEqualTo(1);
                    assertThat(forecast.getCreatedBy()).isEqualTo(99L);
                });
        verify(demandForecastMapper, never()).mergeStagingForecasts(501L, 99L);
    }

    @Test
    void rejectsUnknownModelVersionBeforeReferenceQueries() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L)));
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(null);

        assertThatThrownBy(() -> demandForecastService.importForecasts(request, 99L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEMAND_FORECAST_MODEL_NOT_FOUND));

        verify(demandForecastMapper, never()).countExistingSkus(anyList());
        verify(demandForecastMapper, never()).insertStagingForecasts(anyList());
    }

    @Test
    void rejectsDuplicateSkuAndSalesPointWithinRequest() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L), item(101L, 10L)));
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);

        assertThatThrownBy(() -> demandForecastService.importForecasts(request, 99L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEMAND_FORECAST_DUPLICATE_TARGET));

        verify(demandForecastMapper, never()).countExistingSkus(anyList());
        verify(demandForecastMapper, never()).insertStagingForecasts(anyList());
    }

    @Test
    void rejectsBatchWhenAnySkuDoesNotExist() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L), item(102L, 10L)));
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);
        when(demandForecastMapper.countExistingSkus(anyList())).thenReturn(1);

        assertThatThrownBy(() -> demandForecastService.importForecasts(request, 99L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEMAND_FORECAST_SKU_NOT_FOUND));

        verify(demandForecastMapper, never()).countExistingSalesPoints(anyList());
        verify(demandForecastMapper, never()).insertStagingForecasts(anyList());
    }

    @Test
    void rejectsBatchWhenAnySalesPointDoesNotExist() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L)));
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);
        when(demandForecastMapper.countExistingSkus(anyList())).thenReturn(1);
        when(demandForecastMapper.countExistingSalesPoints(anyList())).thenReturn(0);

        assertThatThrownBy(() -> demandForecastService.importForecasts(request, 99L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DEMAND_FORECAST_SALES_POINT_NOT_FOUND));

        verify(demandForecastMapper, never()).insertStagingForecasts(anyList());
    }

    @Test
    void finalizesOnlyAfterAllBatchesAndItemsAreReceived() {
        DemandForecastImportRequest request = request(
                List.of(item(101L, 10L), item(102L, 10L)), 1, 2L
        );
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);
        when(demandForecastMapper.initializeImportManifest(
                501L, 7L, LocalDate.of(2026, 7, 31), 1, 2L, 99L
        )).thenReturn(1);
        when(demandForecastMapper.countExistingSkus(anyList())).thenReturn(2);
        when(demandForecastMapper.countExistingSalesPoints(anyList())).thenReturn(1);
        when(demandForecastMapper.countReceivedBatches(501L)).thenReturn(1);
        when(demandForecastMapper.sumReceivedItems(501L)).thenReturn(2L);
        when(demandForecastMapper.updateImportProgress(501L, 1, 2L, 2L, 99L)).thenReturn(1);
        when(demandForecastMapper.countStagingForecasts(501L)).thenReturn(2);
        when(demandForecastMapper.markRunSucceeded(501L, 99L)).thenReturn(1);

        DemandForecastImportResponse response = demandForecastService.importForecasts(request, 99L);

        assertThat(response.runStatus()).isEqualTo("SUCCEEDED");
        verify(demandForecastMapper).softDeleteObsoleteForecasts(501L, 99L);
        verify(demandForecastMapper).mergeStagingForecasts(501L, 99L);
    }

    @Test
    void derivesTotalItemsWhenFastApiOmitsIt() {
        DemandForecastImportRequest request = requestWithoutTotalItems(
                List.of(item(101L, 10L), item(102L, 10L))
        );
        when(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .thenReturn(7L);
        when(demandForecastMapper.initializeImportManifest(
                501L, 7L, LocalDate.of(2026, 7, 31), 1, null, 99L
        )).thenReturn(1);
        when(demandForecastMapper.countExistingSkus(anyList())).thenReturn(2);
        when(demandForecastMapper.countExistingSalesPoints(anyList())).thenReturn(1);
        when(demandForecastMapper.countReceivedBatches(501L)).thenReturn(1);
        when(demandForecastMapper.sumReceivedItems(501L)).thenReturn(2L);
        when(demandForecastMapper.updateImportProgress(501L, 1, 2L, 2L, 99L)).thenReturn(1);
        when(demandForecastMapper.countStagingForecasts(501L)).thenReturn(2);
        when(demandForecastMapper.markRunSucceeded(501L, 99L)).thenReturn(1);

        DemandForecastImportResponse response = demandForecastService.importForecasts(request, 99L);

        assertThat(response.runStatus()).isEqualTo("SUCCEEDED");
        verify(demandForecastMapper).updateImportProgress(501L, 1, 2L, 2L, 99L);
        verify(demandForecastMapper).mergeStagingForecasts(501L, 99L);
    }

    @Test
    void acceptsSameBatchPayloadAsIdempotentDuplicate() {
        DemandForecastImportRequest request = request(List.of(item(101L, 10L)));
        when(demandForecastMapper.selectBatchPayloadHash(501L, 1))
                .thenReturn(DemandForecastPayloadHash.calculate(request));

        DemandForecastImportResponse response = demandForecastService.importForecasts(request, 99L);

        assertThat(response.duplicate()).isTrue();
        assertThat(response.importedCount()).isZero();
        verify(demandForecastMapper, never()).insertStagingForecasts(anyList());
    }

    private static DemandForecastImportRequest request(List<DemandForecastImportItemRequest> items) {
        return request(items, 10, 10L);
    }

    private static DemandForecastImportRequest request(
            List<DemandForecastImportItemRequest> items,
            int totalBatches,
            long totalItems
    ) {
        return new DemandForecastImportRequest(
                "purple_monkey_gyk4m5yyxr",
                "stockit-demand-lightgbm",
                "1",
                LocalDate.of(2026, 7, 31),
                1,
                totalBatches,
                totalItems,
                items
        );
    }

    private static DemandForecastImportRequest requestWithoutTotalItems(
            List<DemandForecastImportItemRequest> items
    ) {
        return new DemandForecastImportRequest(
                "purple_monkey_gyk4m5yyxr",
                "stockit-demand-lightgbm",
                "1",
                LocalDate.of(2026, 7, 31),
                1,
                1,
                null,
                items
        );
    }

    private static DemandForecastImportItemRequest item(Long skuId, Long salesPointId) {
        return new DemandForecastImportItemRequest(
                skuId,
                salesPointId,
                new BigDecimal("12.300"),
                new BigDecimal("24.800"),
                new BigDecimal("51.200"),
                new BigDecimal("103.700"),
                new BigDecimal("157.100"),
                "LIGHTGBM",
                "HIGH"
        );
    }
}
