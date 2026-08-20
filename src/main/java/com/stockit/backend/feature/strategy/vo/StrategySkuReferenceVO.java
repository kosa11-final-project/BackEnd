package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 전략 요청 검증과 기본 제목 생성에 필요한 SKU 참조 정보
 */
@Getter
@Setter
public class StrategySkuReferenceVO {

    private Long skuId;
    private String skuName;
}
