package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/** 상세 화면의 액션 위치에 사용할 물류센터 표시 정보 VO */
@Getter
@Setter
public class AiStrategyWarehouseReferenceVO {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
}
