package com.stockit.backend.feature.demandforecast.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 데이터 최신성 및 품질 메타데이터 DTO입니다.
 *
 * @param lastUpdatedAt 데이터 최종 갱신 시각
 * @param isStale 데이터 만료/지연 여부
 * @param dataQualityState 데이터 품질 상태 (AVAILABLE, NO_DATA, STALE, ERROR 등)
 * @param message 상태 설명 메시지
 * @param forecastAsOf 예측 기준일
 */
@Schema(description = "데이터 최신성 및 품질 메타데이터")
public record FreshnessDto(
        @Schema(description = "데이터 최종 갱신 시각")
        Instant lastUpdatedAt,

        @Schema(description = "데이터가 오래되었는지 여부 (Stale)", example = "false")
        boolean isStale,

        @Schema(description = "데이터 품질 상태 (AVAILABLE, NO_DATA, STALE, ERROR). 안전재고 정책 미적재만으로 ERROR가 되지 않습니다.", example = "AVAILABLE")
        String dataQualityState,

        @Schema(description = "설명 메시지", example = "수요예측은 정상 조회되었지만 안전재고 기준이 아직 적재되지 않아 기준선은 표시되지 않습니다.")
        String message,

        @Schema(description = "예측 기준일")
        LocalDate forecastAsOf
) {
    /**
     * 예측 기준일이 없는 이전 버전 호환용 생성자입니다.
     *
     * @param lastUpdatedAt 데이터 최종 갱신 시각
     * @param isStale 데이터 만료 여부
     * @param dataQualityState 데이터 품질 상태
     * @param message 설명 메시지
     */
    public FreshnessDto(Instant lastUpdatedAt, boolean isStale, String dataQualityState, String message) {
        this(lastUpdatedAt, isStale, dataQualityState, message, null);
    }
}
