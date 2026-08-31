package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;

import lombok.Getter;
import lombok.Setter;

/** Teams 운영 알림에 필요한 최종 실패 Case와 표시 정보를 한 번에 조회하는 객체 */
@Getter
@Setter
public class AiStrategyFailureAlertVO {

    private Long strategyCaseId;
    private Long retryParentCaseId;
    private String caseCode;
    private String caseName;
    private String requestPayloadJson;
    private StrategyGenerationStage generationStage;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime completedAt;

    private Long requesterId;
    private String requesterName;

    private Long skuId;
    private String skuCode;
    private String skuName;

    private Long sourceSalesPointId;
    private String sourceSalesPointName;
}
