package com.stockit.backend.feature.strategy.vo;

import lombok.Getter;
import lombok.Setter;

/** Teams 승인 요청 수신인 선택에 사용하는 활성 조직 사용자. */
@Getter
@Setter
public class AiStrategyReviewerVO {

    private Long reviewerId;
    private String reviewerName;
    private String email;
    private Long organizationId;
    private String organizationName;
    private String roleName;
}
