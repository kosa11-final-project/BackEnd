package com.stockit.backend.feature.inventorysync.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventorysync.InventorySyncHash;
import com.stockit.backend.feature.inventorysync.InventorySyncLockSupport;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncStateQueryMapper;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunSourceVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSourceStateVO;

/** 버튼 요청을 durable run으로 먼저 기록하는 서비스입니다. */
@Service
public class InventorySyncSubmissionService {

    public static final String ACTIVE_SCOPE = "INVENTORY_CANONICAL";
    private static final int MIN_REQUEST_INTERVAL_SECONDS = 10;
    private static final int MAX_REQUESTS_PER_HOUR = 60;

    private final InventorySyncRunMapper runMapper;
    private final InventorySyncBatchLauncher launcher;
    private final InventorySyncStateQueryMapper stateQueryMapper;
    private final InventorySyncRunControlService runControl;
    private final InventorySyncSnapshotStatusService snapshotStatusService;

    @Autowired
    public InventorySyncSubmissionService(
            InventorySyncRunMapper runMapper,
            InventorySyncBatchLauncher launcher,
            InventorySyncStateQueryMapper stateQueryMapper,
            InventorySyncRunControlService runControl,
            InventorySyncSnapshotStatusService snapshotStatusService
    ) {
        this.runMapper = runMapper;
        this.launcher = launcher;
        this.stateQueryMapper = stateQueryMapper;
        this.runControl = runControl;
        this.snapshotStatusService = snapshotStatusService;
    }

    @Transactional
    public SubmissionResult submit(InventorySyncStartRequest request, Long requestedBy) {
        return submitInternal(request, requestedBy, "MANUAL", true);
    }

    /** ML 입력용 일일 자동 동기화입니다. 수동 요청 rate limit은 적용하지 않습니다. */
    @Transactional
    public SubmissionResult submitScheduled(InventorySyncStartRequest request) {
        Long systemUserId = runMapper.selectSystemUserId();
        if (systemUserId == null || systemUserId <= 0) {
            throw new IllegalStateException("__system__ user is required for scheduled inventory sync");
        }
        return submitInternal(request, systemUserId, "SCHEDULED", false);
    }

    private SubmissionResult submitInternal(InventorySyncStartRequest request, Long requestedBy,
                                            String triggerType, boolean enforceRateLimit) {
        if (requestedBy == null || requestedBy <= 0) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
        // active scope 조회와 run 삽입 사이의 경쟁 구간을 source state의 고정 행으로 직렬화합니다.
        // active_scope_key UNIQUE 제약은 최종 방어선으로 유지합니다.
        try {
            if (runMapper.lockSubmissionGuard() != 1) {
                throw new IllegalStateException("inventory source state is not initialized");
            }
        } catch (DataAccessException exception) {
            if (InventorySyncLockSupport.isLockWaitFailure(exception)) {
                throw InventorySyncLockSupport.conflict();
            }
            throw exception;
        }
        String hash = requestHash(request);
        InventorySyncRunVO existing = runMapper.selectByClientRequestId(request.clientRequestId());
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) {
                return new SubmissionResult(409, withState(existing));
            }
            return new SubmissionResult(200, withState(existing));
        }
        if (enforceRateLimit) {
            Instant now = Instant.now();
            Instant lastRequestedAt = runMapper.selectLastRequestedAt(requestedBy);
            if (lastRequestedAt != null && lastRequestedAt.plusSeconds(MIN_REQUEST_INTERVAL_SECONDS).isAfter(now)) {
                long retryAfterSeconds = Math.max(1, Duration.between(now, lastRequestedAt.plusSeconds(MIN_REQUEST_INTERVAL_SECONDS)).getSeconds() + 1);
                return new SubmissionResult(429, null, retryAfterSeconds);
            }
            if (runMapper.countRecentRequests(requestedBy, now.minus(Duration.ofHours(1))) >= MAX_REQUESTS_PER_HOUR) {
                return new SubmissionResult(429, null, MIN_REQUEST_INTERVAL_SECONDS);
            }
        }
        InventorySyncRunVO active = runMapper.selectActiveRun();
        if (active != null) {
            return new SubmissionResult(409, withState(active));
        }
        InventorySyncRunVO run = new InventorySyncRunVO();
        run.setClientRequestId(request.clientRequestId());
        run.setRequestHash(hash);
        run.setRequestedBy(requestedBy);
        run.setTriggerType(triggerType);
        run.setRunStatus("QUEUED");
        runMapper.insertRun(run);
        Long runId = run.getInventorySyncRunId();
        // 커밋 전에 비동기 워커를 깨우면 워커가 아직 커밋되지 않은 run을 읽지 못할 수 있습니다.
        // durable 큐 행이 먼저 커밋된 뒤에만 워커를 실행해 재시작/재시도 경로와 같은 의미를 보장합니다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (!launcher.launch(runId)) {
                        markLaunchFailed(runId, run.getMainAttemptNo(), run.getFencingToken(),
                                "worker queue가 가득 차 실행을 등록하지 못했습니다.");
                    }
                }
            });
        } else {
            if (!launcher.launch(runId)) {
                markLaunchFailed(runId, run.getMainAttemptNo(), run.getFencingToken(),
                        "worker queue가 가득 차 실행을 등록하지 못했습니다.");
            }
        }
        return new SubmissionResult(202, InventorySyncRunResponse.from(run));
    }

    public InventorySyncRunResponse latest() {
        InventorySyncRunVO active = runMapper.selectActiveRun();
        if (active != null) return withState(active);
        InventorySyncRunVO latest = runMapper.selectLatestRun();
        return latest == null ? null : withState(latest);
    }

    public InventorySyncRunResponse get(Long runId) {
        InventorySyncRunVO run = runMapper.selectById(runId);
        return run == null ? null : withState(run);
    }

    private InventorySyncRunResponse withState(InventorySyncRunVO run) {
        List<InventorySyncSourceStateVO> states = stateQueryMapper.selectSourceStates();
        List<InventorySyncRunSourceVO> sources = stateQueryMapper.selectRunSources(run.getInventorySyncRunId());
        var snapshotRefresh = snapshotStatusService.resolve(run);
        return InventorySyncRunResponse.from(run, states, sources, snapshotRefresh);
    }

    public static String requestHash(InventorySyncStartRequest request) {
        return InventorySyncHash.sha256Hex(request.clientRequestId().trim());
    }

    private void markLaunchFailed(Long runId, int attemptNo, long fencingToken, String message) {
        runControl.markLaunchFailed(runId, attemptNo, fencingToken, message);
    }

    public record SubmissionResult(int httpStatus, InventorySyncRunResponse response, long retryAfterSeconds) {
        public SubmissionResult(int httpStatus, InventorySyncRunResponse response) {
            this(httpStatus, response, 0);
        }
    }
}
