package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/** 상세 화면의 요청 조건과 액션 위치에 사용할 판매처 표시 정보 VO */
@Getter
@Setter
public class AiStrategySalesPointReferenceVO {

    private Long salesPointId;
    private String salesPointCode;
    private String salesPointName;
}
