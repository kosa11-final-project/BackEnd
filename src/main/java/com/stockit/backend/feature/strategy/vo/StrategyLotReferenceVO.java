package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 선택 LOT의 소속 SKU와 사용 가능 여부를 검증하기 위한 참조 정보
 */
@Getter
@Setter
public class StrategyLotReferenceVO {

    private Long lotId;
    private Long skuId;
    private Long warehouseId;
    private String lotStatus;
}
