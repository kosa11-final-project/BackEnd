package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ActionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExistingSelectionRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExecutionResultWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.FinalSelectionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ForecastSnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.InventorySnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.LotAllocationWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.OptionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalAction;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalHeader;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PriceSnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.SimulationWrite;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;

@Mapper
public interface StrategyApprovalMapper {

    CaseRecord selectCaseForUpdate(@Param("strategyCaseId") Long strategyCaseId);

    ExistingSelectionRecord selectExistingSelection(
            @Param("strategyCaseId") Long strategyCaseId
    );

    PersistedApprovalHeader selectPersistedApprovalHeader(
            @Param("strategyCaseId") Long strategyCaseId
    );

    List<PersistedApprovalAction> selectPersistedApprovalActions(
            @Param("strategyOptionId") Long strategyOptionId
    );

    void insertOption(OptionWrite option);

    void insertSimulation(SimulationWrite simulation);

    void insertAction(ActionWrite action);

    void insertLotAllocation(LotAllocationWrite allocation);

    void insertInventorySnapshot(InventorySnapshotWrite snapshot);

    void insertPriceSnapshot(PriceSnapshotWrite snapshot);

    void insertFinalSelection(FinalSelectionWrite selection);

    void insertForecastSnapshot(ForecastSnapshotWrite snapshot);

    void insertExecutionResult(ExecutionResultWrite result);

    void insertReviewRequest(ReviewRequestWrite request);

    List<ReviewRequestRecord> selectReviewRequests(
            @Param("strategyOptionId") Long strategyOptionId,
            @Param("reviewerIds") List<Long> reviewerIds
    );

    List<ReviewRequestRecord> selectAllReviewRequests(
            @Param("strategyOptionId") Long strategyOptionId
    );

    List<ReviewRequestRecord> selectReviewRequestDeliveries(
            @Param("strategyOptionId") Long strategyOptionId
    );

    ReviewRequestRecord selectReviewRequest(
            @Param("reviewRequestId") Long reviewRequestId
    );

    int claimReviewRequest(
            @Param("reviewRequestId") Long reviewRequestId,
            @Param("actorId") Long actorId,
            @Param("claimTimeoutSeconds") long claimTimeoutSeconds
    );

    int completeReviewRequest(
            @Param("reviewRequestId") Long reviewRequestId,
            @Param("reviewStatus") StrategyReviewStatus reviewStatus,
            @Param("actorId") Long actorId
    );

    int markReadyToExecuteIfAllSent(
            @Param("strategyCaseId") Long strategyCaseId,
            @Param("strategyOptionId") Long strategyOptionId,
            @Param("actorId") Long actorId
    );
}
