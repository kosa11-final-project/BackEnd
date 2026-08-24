package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/** 상세 화면에 표시할 LOT 식별자와 업무 코드 조회 VO */
@Getter
@Setter
public class AiStrategyLotDisplayVO {

    private Long lotId;
    private String lotCode;
}
