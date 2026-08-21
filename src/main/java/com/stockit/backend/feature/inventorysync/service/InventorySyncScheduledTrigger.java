package com.stockit.backend.feature.inventorysync.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;

/** 매일 00:30 KST에 ML 입력용 원천 동기화를 등록합니다. */
@Component
@ConditionalOnProperty(prefix = "app.inventory-sync.schedule", name = "enabled", havingValue = "true")
public class InventorySyncScheduledTrigger {
    private static final Logger log = LoggerFactory.getLogger(InventorySyncScheduledTrigger.class);
    private static final ZoneId DEFAULT_BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventorySyncSubmissionService submissionService;
    private final ZoneId businessZone;

    @Autowired
    public InventorySyncScheduledTrigger(InventorySyncSubmissionService submissionService,
                                         @Value("${app.inventory-sync.schedule.zone:Asia/Seoul}") String zone) {
        this.submissionService = submissionService;
        this.businessZone = ZoneId.of(zone);
    }

    public InventorySyncScheduledTrigger(InventorySyncSubmissionService submissionService) {
        this(submissionService, DEFAULT_BUSINESS_ZONE.getId());
    }

    @Scheduled(
            cron = "${app.inventory-sync.schedule.cron:0 30 0 * * *}",
            zone = "${app.inventory-sync.schedule.zone:Asia/Seoul}"
    )
    public void trigger() {
        LocalDate businessDate = LocalDate.now(businessZone);
        String clientRequestId = scheduledClientRequestId(businessDate);
        var result = submissionService.submitScheduled(new InventorySyncStartRequest(clientRequestId));
        log.info("scheduled inventory sync registered: requestId={}, status={}, runId={}",
                clientRequestId, result.httpStatus(), result.response() == null ? null : result.response().syncRunId());
    }

    public static String scheduledClientRequestId(LocalDate businessDate) {
        return "inventory-sync-scheduled-" + ID_DATE.format(businessDate);
    }
}
