package com.stockit.backend.feature.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.dashboard.vo.DashboardSnapshotVO;

@Mapper
public interface DashboardSnapshotMapper {

    Long selectNextSnapshotId();

    Long selectSnapshotIdBySyncJobId(@Param("syncJobId") Long syncJobId);

    int insertSnapshot(
            @Param("snapshotId") Long snapshotId,
            @Param("syncJobId") Long syncJobId,
            @Param("payloadVersion") int payloadVersion,
            @Param("payloadJson") String payloadJson
    );

    DashboardSnapshotVO selectLatestSnapshot();
}
