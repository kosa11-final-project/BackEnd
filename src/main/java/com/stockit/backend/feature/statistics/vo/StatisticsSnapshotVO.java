package com.stockit.backend.feature.statistics.vo;

import java.time.LocalDate;

import com.stockit.backend.common.persistence.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatisticsSnapshotVO extends BaseEntity {

    private Long statisticsSnapshotId;
    private Long syncJobId;
    private LocalDate asOfDate;
    private String scopeType;
    private Long warehouseId;
    private Long salesPointId;
    private String scopeCode;
    private String scopeName;
    private String regionCode;
    private int payloadVersion;
    private String payloadJson;
}
