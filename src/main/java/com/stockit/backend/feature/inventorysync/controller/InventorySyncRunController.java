package com.stockit.backend.feature.inventorysync.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;
import com.stockit.backend.feature.inventorysync.InventorySyncRoutes;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(InventorySyncRoutes.RUNS)
public class InventorySyncRunController {
    private final InventorySyncSubmissionService service;

    public InventorySyncRunController(InventorySyncSubmissionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> start(
            @Valid @RequestBody InventorySyncStartRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        var result = service.submit(request, principal == null ? null : principal.getUserId());
        if (result.httpStatus() == 429) {
            long retryAfter = result.retryAfterSeconds() > 0 ? result.retryAfterSeconds() : 10;
            return ResponseEntity.status(429)
                    .header("Retry-After", Long.toString(retryAfter))
                    .body(ApiErrorResponse.of(ErrorCode.RATE_LIMITED, "동기화 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.", InventorySyncRoutes.RUNS));
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.httpStatus());
        if (result.response() != null && result.httpStatus() == 202) {
            builder.location(URI.create(InventorySyncRoutes.RUNS + "/" + result.response().syncRunId()));
        }
        return builder.body(ApiResponse.of(result.response()));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<InventorySyncRunResponse>> latest() {
        InventorySyncRunResponse response = service.latest();
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/{syncRunId}")
    public ResponseEntity<ApiResponse<InventorySyncRunResponse>> get(@PathVariable Long syncRunId) {
        InventorySyncRunResponse response = service.get(syncRunId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(ApiResponse.of(response));
    }
}
