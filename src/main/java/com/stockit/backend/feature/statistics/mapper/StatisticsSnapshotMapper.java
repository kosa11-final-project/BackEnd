package com.stockit.backend.feature.statistics.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;
import com.stockit.backend.feature.statistics.vo.StatisticsSnapshotVO;

@Mapper
public interface StatisticsSnapshotMapper {

    Long selectNextSnapshotId();

    List<Long> selectSnapshotIdsBySyncJobId(@Param("syncJobId") Long syncJobId);

    int insertSnapshot(
            @Param("snapshotId") Long snapshotId,
            @Param("syncJobId") Long syncJobId,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("aggregate") InventoryStatisticsAggregateVO aggregate,
            @Param("payloadVersion") int payloadVersion,
            @Param("payloadJson") String payloadJson
    );

    List<StatisticsSnapshotVO> selectLatestSnapshots(@Param("toDate") LocalDate toDate);

    List<StatisticsSnapshotVO> selectTrendSnapshots(
            @Param("scopeType") String scopeType,
            @Param("scopeCode") String scopeCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
