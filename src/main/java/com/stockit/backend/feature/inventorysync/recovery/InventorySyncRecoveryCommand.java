package com.stockit.backend.feature.inventorysync.recovery;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventorysync.service.InventorySyncRecoveryService;

/** 운영 콘솔/배포 스크립트가 호출하는 recovery command boundary입니다. HTTP controller로 노출하지 않습니다. */
@Component
public class InventorySyncRecoveryCommand {
    private final InventorySyncRecoveryService service;

    public InventorySyncRecoveryCommand(InventorySyncRecoveryService service) {
        this.service = service;
    }

    public boolean execute(Long runId, long expectedFencingToken, String operator) {
        return service.recover(runId, operator, expectedFencingToken, Instant.now());
    }
}
