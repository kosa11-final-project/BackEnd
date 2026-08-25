package com.stockit.backend.feature.inventorysync.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunMapper;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRunSourceMapper;

/** 비동기 callback/worker 실패를 별도 control transaction으로 확정합니다. */
@Service
public class InventorySyncRunControlService {
    private final InventorySyncRunMapper runMapper;
    private final InventorySyncRunSourceMapper runSourceMapper;

    public InventorySyncRunControlService(InventorySyncRunMapper runMapper, InventorySyncRunSourceMapper runSourceMapper) {
        this.runMapper = runMapper;
        this.runSourceMapper = runSourceMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLaunchFailed(Long runId, String message) {
        var current = runMapper.selectByIdForUpdate(runId);
        if (current != null && "QUEUED".equals(current.getRunStatus())) {
            runMapper.markLaunchFailed(runId, persistentMessage(message));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLaunchFailed(Long runId, int attemptNo, long fencingToken, String message) {
        var current = runMapper.selectByIdForUpdate(runId);
        if (current != null && "QUEUED".equals(current.getRunStatus())
                && current.getMainAttemptNo() == attemptNo
                && current.getFencingToken() == fencingToken) {
            runMapper.markLaunchFailed(runId, persistentMessage(message));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWorkerFailed(Long runId, String sourceType, String phase, String errorCode,
                                 String message, int attemptNo, long fencingToken, String status) {
        // A worker can report an error after recovery has already fenced it out.
        // Lock and compare the durable run row before touching run-source/error data;
        // otherwise a stale worker could mark the replacement attempt as FAILED.
        var current = runMapper.selectByIdForUpdate(runId);
        if (current == null || !"RUNNING".equals(current.getRunStatus())
                || current.getMainAttemptNo() != attemptNo
                || current.getFencingToken() != fencingToken) {
            return;
        }
        String persistedMessage = persistentMessage(message);
        runSourceMapper.markFailed(runId);
        runMapper.insertError(runId, sourceType, phase, errorCode, persistedMessage);
        runMapper.complete(runId, attemptNo, fencingToken, status, 0, errorCode, persistedMessage);
    }

    private String persistentMessage(String message) {
        if (message == null) return null;
        // Oracle columns are VARCHAR2(2000 BYTE). 500 Unicode code units fit
        // even when every character occupies four UTF-8 bytes.
        return message.substring(0, Math.min(500, message.length()));
    }
}
