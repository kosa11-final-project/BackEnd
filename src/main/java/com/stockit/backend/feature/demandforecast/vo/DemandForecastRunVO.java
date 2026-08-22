package com.stockit.backend.feature.demandforecast.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 수요예측 오케스트레이션 실행과 배치 수신 진행 상태입니다. */
@Getter
@Setter
public class DemandForecastRunVO {
    private Long forecastRunId;
    private String clientRequestId;
    private String scheduleKey;
    private String triggerType;
    private LocalDate baseDate;
    private Long modelVersionId;
    private String azureJobId;
    private String inputBlobUrl;
    private String runStatus;
    private String currentStage;
    private Integer totalBatches;
    private Long totalItems;
    private int receivedBatches;
    private long receivedItems;
    private Long exportJobExecutionId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long createdBy;
    private Long updatedBy;
}
