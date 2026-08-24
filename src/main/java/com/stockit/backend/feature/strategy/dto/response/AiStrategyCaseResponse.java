package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyLotDisplayVO;
import com.stockit.backend.feature.strategy.vo.AiStrategySalesPointReferenceVO;

/** AI 전략 상세 화면의 상단 정보·요청 조건·생성 결과를 제공하는 응답 DTO */
public record AiStrategyCaseResponse(
        Long strategyCaseId,
        String caseName,
        StrategyCaseStatus caseStatus,
        StrategyGenerationStage generationStage,
        Sku sku,
        Requester requester,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime resultExpiresAt,
        RequestConditions requestConditions,
        AiStrategyGenerationResultResponse result
) {
    /**
     * Case 영속 정보와 요청 스냅샷, 마스터 표시명을 하나의 상세 응답으로 조합
     *
     * <p>사용자 우선순위를 화면에서도 재현할 수 있도록 요청 당시 ID 순서를 유지</p>
     */
    public static AiStrategyCaseResponse from(
            AiStrategyCaseDetailVO strategyCase,
            StrategyCaseRequestPayload payload,
            Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
            Map<Long, AiStrategyLotDisplayVO> lots,
            AiStrategyGenerationResultResponse result
    ) {
        Category category = strategyCase.getCategoryId() == null
                ? null
                : new Category(
                        strategyCase.getCategoryId(),
                        strategyCase.getCategoryName(),
                        strategyCase.getCategoryLevel()
                );
        return new AiStrategyCaseResponse(
                strategyCase.getStrategyCaseId(),
                strategyCase.getCaseName(),
                strategyCase.getCaseStatus(),
                strategyCase.getGenerationStage(),
                new Sku(
                        strategyCase.getSkuId(),
                        strategyCase.getSkuCode(),
                        strategyCase.getSkuName(),
                        strategyCase.getImageUrl(),
                        category
                ),
                new Requester(
                        strategyCase.getRequesterId(),
                        strategyCase.getRequesterName()
                ),
                strategyCase.getFailureCode(),
                strategyCase.getFailureMessage(),
                strategyCase.getCreatedAt(),
                strategyCase.getCompletedAt(),
                strategyCase.getResultExpiresAt(),
                RequestConditions.from(
                        strategyCase.getRequestedSalesPointId(),
                        payload,
                        salesPoints,
                        lots
                ),
                result
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

    /** 사용자 입력값과 생성 시 확정된 수요예측 기간을 함께 제공하는 요청 조건 */
    public record RequestConditions(
            SalesPoint sourceSalesPoint,
            List<Lot> lots,
            List<SalesPoint> candidateSalesPoints,
            List<StrategyType> strategyTypes,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            LocalDate forecastStartDate,
            LocalDate forecastEndDate
    ) {
        private static RequestConditions from(
                Long sourceSalesPointId,
                StrategyCaseRequestPayload payload,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            return new RequestConditions(
                    SalesPoint.from(sourceSalesPointId, salesPoints),
                    payload.lotIds().stream()
                            .map(lotId -> Lot.from(lotId, lots))
                            .toList(),
                    payload.candidateSalesPointIds().stream()
                            .map(id -> SalesPoint.from(id, salesPoints))
                            .toList(),
                    payload.strategyTypes(),
                    payload.preferredStartDate(),
                    payload.preferredEndDate(),
                    payload.forecastStartDate(),
                    payload.forecastEndDate()
            );
        }
    }

    public record SalesPoint(
            Long salesPointId,
            String salesPointCode,
            String salesPointName
    ) {
        private static SalesPoint from(
                Long salesPointId,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints
        ) {
            if (salesPointId == null) {
                return null;
            }
            AiStrategySalesPointReferenceVO point = salesPoints.get(salesPointId);
            return new SalesPoint(
                    salesPointId,
                    point == null ? null : point.getSalesPointCode(),
                    point == null ? null : point.getSalesPointName()
            );
        }
    }

    public record Lot(Long lotId, String lotCode) {
        private static Lot from(
                Long lotId,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            AiStrategyLotDisplayVO lot = lots.get(lotId);
            return new Lot(lotId, lot == null ? null : lot.getLotCode());
        }
    }
}
