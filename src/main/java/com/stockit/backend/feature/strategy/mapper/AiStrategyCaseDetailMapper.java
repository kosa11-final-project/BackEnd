package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyLotDisplayVO;
import com.stockit.backend.feature.strategy.vo.AiStrategySalesPointReferenceVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyWarehouseReferenceVO;

/** 상세 응답 표시명 보강에 필요한 Case와 마스터 참조를 조회하는 MyBatis Mapper */
@Mapper
public interface AiStrategyCaseDetailMapper {

    /** 상품·카테고리·요청자까지 결합한 Case 상세 헤더 조회 */
    AiStrategyCaseDetailVO selectCaseDetail(
            @Param("strategyCaseId") Long strategyCaseId
    );

    /** 요청 조건과 액션에 포함된 판매처 표시 정보 일괄 조회 */
    List<AiStrategySalesPointReferenceVO> selectSalesPoints(
            @Param("salesPointIds") List<Long> salesPointIds
    );

    /** 액션에 포함된 물류센터 표시 정보 일괄 조회 */
    List<AiStrategyWarehouseReferenceVO> selectWarehouses(
            @Param("warehouseIds") List<Long> warehouseIds
    );

    /** 요청 LOT와 액션 할당 LOT의 표시 정보 일괄 조회 */
    List<AiStrategyLotDisplayVO> selectLots(@Param("lotIds") List<Long> lotIds);
}
