package com.stockit.backend.feature.demandforecast.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;

import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportItemRequest;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;

/** FastAPI 배치 재전송을 판별하기 위한 순서 독립적 SHA-256 해시입니다. */
public final class DemandForecastPayloadHash {
    private DemandForecastPayloadHash() {
    }

    public static String calculate(DemandForecastImportRequest request) {
        StringBuilder canonical = new StringBuilder()
                .append(request.azureJobId()).append('|')
                .append(request.modelName()).append('|')
                .append(request.modelVersion()).append('|')
                .append(request.forecastBaseDate()).append('|')
                .append(request.batchNumber()).append('|')
                .append(request.totalBatches()).append('|')
                .append(request.totalItems());

        request.forecasts().stream()
                .sorted(Comparator.comparing(DemandForecastImportItemRequest::skuId)
                        .thenComparing(DemandForecastImportItemRequest::salesPointId))
                .forEach(item -> canonical
                        .append('\n').append(item.skuId())
                        .append('|').append(item.salesPointId())
                        .append('|').append(decimal(item.predictedQtyD7()))
                        .append('|').append(decimal(item.predictedQtyD14()))
                        .append('|').append(decimal(item.predictedQtyD30()))
                        .append('|').append(decimal(item.predictedQtyD60()))
                        .append('|').append(decimal(item.predictedQtyD90()))
                        .append('|').append(item.forecastSource())
                        .append('|').append(item.confidenceLevel()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
