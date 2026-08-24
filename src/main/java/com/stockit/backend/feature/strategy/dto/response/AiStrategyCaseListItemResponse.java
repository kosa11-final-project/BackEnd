package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListVO;

public record AiStrategyCaseListItemResponse(
        Long strategyCaseId,
        String caseName,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage,
        Sku sku,
        Requester requester,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime resultExpiresAt,
        Failure failure
) {
    public static AiStrategyCaseListItemResponse from(AiStrategyCaseListVO value) {
        Failure failure = value.getFailureCode() == null && value.getFailureMessage() == null
                ? null
                : new Failure(value.getFailureCode(), value.getFailureMessage(), value.getCompletedAt());
        Category category = value.getCategoryId() == null
                ? null
                : new Category(value.getCategoryId(), value.getCategoryName(), value.getCategoryLevel());
        return new AiStrategyCaseListItemResponse(
                value.getStrategyCaseId(),
                value.getCaseName(),
                value.getCaseStatus(),
                value.getGenerationStage(),
                new Sku(value.getSkuId(), value.getSkuCode(), value.getSkuName(), value.getImageUrl(), category),
                new Requester(value.getRequesterId(), value.getRequesterName()),
                value.getCreatedAt(),
                value.getCompletedAt(),
                value.getResultExpiresAt(),
                failure
        );
    }

    public record Sku(
            Long skuId,
            String skuCode,
            String skuName,
            String imageUrl,
            Category category
    ) {
    }

    public record Category(Long categoryId, String categoryName, Integer categoryLevel) {
    }

    public record Requester(Long userId, String userName) {
    }

    public record Failure(String code, String message, LocalDateTime failedAt) {
    }
}
