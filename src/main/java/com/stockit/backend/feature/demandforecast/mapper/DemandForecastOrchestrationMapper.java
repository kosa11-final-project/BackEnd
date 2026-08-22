package com.stockit.backend.feature.demandforecast.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;

@Mapper
public interface DemandForecastOrchestrationMapper {
    Long selectSystemUserId();

    int insertScheduledRun(
            @Param("clientRequestId") String clientRequestId,
            @Param("scheduleKey") String scheduleKey,
            @Param("baseDate") LocalDate baseDate,
            @Param("userId") Long userId
    );

    DemandForecastRunVO selectByClientRequestId(@Param("clientRequestId") String clientRequestId);

    List<DemandForecastRunVO> selectAzurePollingRuns();

    List<DemandForecastRunVO> selectTimedOutRuns(@Param("timeoutSeconds") long timeoutSeconds);

    int markExportCompleted(
            @Param("runId") Long runId,
            @Param("jobExecutionId") Long jobExecutionId,
            @Param("blobUrl") String blobUrl,
            @Param("userId") Long userId
    );

    int markAzureSubmitted(
            @Param("runId") Long runId,
            @Param("azureJobId") String azureJobId,
            @Param("userId") Long userId
    );

    int touchAzurePolling(@Param("runId") Long runId, @Param("userId") Long userId);

    int markImportRequested(@Param("runId") Long runId, @Param("userId") Long userId);

    int markFailed(
            @Param("runId") Long runId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("userId") Long userId
    );
}
