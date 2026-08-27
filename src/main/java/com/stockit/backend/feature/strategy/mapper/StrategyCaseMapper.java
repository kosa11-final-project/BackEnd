package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;
import com.stockit.backend.feature.strategy.vo.StrategySkuReferenceVO;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;
import java.time.LocalDateTime;

/**
 * AI 전략 생성 요청과 요청 대상 참조 정보를 조회하는 MyBatis Mapper
 */
@Mapper
public interface StrategyCaseMapper {

    /**
     * 삭제되거나 비활성화된 SKU가 전략 생성 대상으로 사용되지 않도록 조회
     */
    StrategySkuReferenceVO selectActiveSku(@Param("skuId") Long skuId);

    /**
     * 요청된 판매처를 한 번에 검증하기 위한 활성 판매처 ID 조회
     */
    List<Long> selectActiveSalesPointIds(@Param("salesPointIds") List<Long> salesPointIds);

    /**
     * 후보 미지정 수요예측의 기대 범위를 확정하기 위해 해당 SKU의 가용 재고가
     * 존재하는 활성 판매처를 조회
     */
    List<Long> selectActiveSalesPointIdsBySkuInventory(@Param("skuId") Long skuId);

    /**
     * LOT 존재 여부와 대상 SKU 소속 여부를 한 번에 검증하기 위한 조회
     */
    List<StrategyLotReferenceVO> selectLotReferences(@Param("lotIds") List<Long> lotIds);

    /**
     * 생성 요청을 저장하고 DB IDENTITY로 발급된 식별자를 객체에 반영
     */
    void insertStrategyCase(StrategyCaseVO strategyCase);

    /**
     * DB 기본값을 포함한 저장 결과 확인을 위한 단건 조회
     */
    StrategyCaseVO selectStrategyCaseById(@Param("strategyCaseId") Long strategyCaseId);

    /**
     * 아직 Worker가 선점하지 않은 생성 Case만 수요예측 단계로 전이
     */
    int markForecastingIfPending(@Param("strategyCaseId") Long strategyCaseId);

    /**
     * Redis 예측 체크포인트가 저장된 FORECASTING Case만 다음 단계로 전이
     */
    int markStrategyGeneratingIfForecasting(
            @Param("strategyCaseId") Long strategyCaseId
    );

    /** 최종 Redis 결과를 가리키며 생성 완료 상태로 조건부 전환 */
    int markGeneratedIfStrategyGenerating(
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("resultCacheKey") String resultCacheKey,
            @Param("resultExpiresAt") LocalDateTime resultExpiresAt,
            @Param("recommendationOutcome") StrategyRecommendationOutcome recommendationOutcome
    );

    /**
     * 생성 중인 Case에 한해 최종 실패 상태와 원인을 기록
     */
    int markGenerationFailedIfGenerating(
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    /**
     * 늦은 중복 오류가 다음 단계 Case를 덮어쓰지 않도록 예상 단계까지 비교
     */
    int markGenerationFailedAtStage(
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("expectedStage") StrategyGenerationStage expectedStage,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );
}
