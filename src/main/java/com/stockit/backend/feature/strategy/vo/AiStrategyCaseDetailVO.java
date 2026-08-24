package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/** AI 전략 상세 화면의 Case·상품·요청자 정보를 한 번에 조회하는 VO */
@Getter
@Setter
public class AiStrategyCaseDetailVO extends StrategyCaseVO {

    private String skuCode;
    private String skuName;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Integer categoryLevel;
    private Long requesterId;
    private String requesterName;
}
