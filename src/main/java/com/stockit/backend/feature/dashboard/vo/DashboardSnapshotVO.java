package com.stockit.backend.feature.dashboard.vo;

import com.stockit.backend.common.persistence.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardSnapshotVO extends BaseEntity {

    private Long dashboardSnapshotId;
    private Long syncJobId;
    private int payloadVersion;
    private String payloadJson;
}
