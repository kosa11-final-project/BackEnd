package com.stockit.backend.feature.strategy.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 전략 실행 목록 페이지")
public record StrategyExecutionPageResponse(
        List<StrategyExecutionResponse> content,
        @Schema(example = "0") int page,
        @Schema(example = "10") int size,
        @Schema(example = "125") long totalElements,
        @Schema(example = "13") int totalPages,
        @Schema(example = "true") boolean first,
        @Schema(example = "false") boolean last
) {
    public StrategyExecutionPageResponse {
        content = List.copyOf(content == null ? List.of() : content);
    }
}
