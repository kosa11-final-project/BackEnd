package com.stockit.backend.feature.strategy.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최종 선택 전 AI 전략 생성 Case 목록 페이지")
public record AiStrategyCaseListPageResponse(
        List<AiStrategyCaseListItemResponse> content,
        StatusCounts statusCounts,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public AiStrategyCaseListPageResponse {
        content = List.copyOf(content == null ? List.of() : content);
    }

    public record StatusCounts(
            long all,
            long generating,
            long generated,
            long generationFailed
    ) {
    }
}
