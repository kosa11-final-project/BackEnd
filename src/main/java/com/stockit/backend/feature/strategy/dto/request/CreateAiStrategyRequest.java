package com.stockit.backend.feature.strategy.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "비동기 AI 전략 생성 요청")
public record CreateAiStrategyRequest(
        @Size(max = 200)
        @Schema(description = "전략 이름. 비우면 서버가 SKU명과 요청 시각으로 생성")
        String caseName,

        @NotNull @Positive
        @Schema(description = "대상 SKU ID", example = "1001")
        Long skuId,

        @Positive
        @Schema(description = "현재 판매처 ID. 공용 미할당 재고면 null")
        Long sourceSalesPointId,

        @Schema(description = "대상 LOT ID. 비우면 서버가 대상 재고를 결정")
        List<@Positive Long> lotIds,

        @Schema(description = "희망 판매처 우선순위. 비우면 해당 SKU의 가용 재고가 존재하는 활성 판매처")
        List<@Positive Long> candidateSalesPointIds,

        @Schema(description = "희망 전략 유형 우선순위. 비우면 전체 지원 유형")
        List<@NotNull StrategyType> strategyTypes,

        @Schema(description = "희망 판매 시작일")
        LocalDate preferredStartDate,

        @Schema(description = "희망 판매 종료일. 시작일부터 포함 90일 이내")
        LocalDate preferredEndDate
) {
    public CreateStrategyCaseCommand toCommand() {
        return new CreateStrategyCaseCommand(
                caseName, skuId, sourceSalesPointId, lotIds, candidateSalesPointIds,
                strategyTypes, preferredStartDate, preferredEndDate
        );
    }
}
