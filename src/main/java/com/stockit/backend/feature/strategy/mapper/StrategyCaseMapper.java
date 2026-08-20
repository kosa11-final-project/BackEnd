package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;
import com.stockit.backend.feature.strategy.vo.StrategySkuReferenceVO;

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
}
