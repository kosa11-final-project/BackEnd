package com.stockit.backend.feature.inventorysync.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.inventorysync.InventorySyncRoutes;

import jakarta.validation.Valid;

@RestController
@RequestMapping(InventorySyncRoutes.DEMO_ADJUSTMENTS)
@ConditionalOnProperty(prefix = "app.inventory-sync", name = "demo-enabled", havingValue = "true")
public class InventoryDemoAdjustmentController {
    private final InventoryDemoAdjustmentService service;
    public InventoryDemoAdjustmentController(InventoryDemoAdjustmentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryDemoAdjustmentResponse>> apply(
            @Valid @RequestBody InventoryDemoAdjustmentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.of(service.apply(request, principal == null ? null : principal.getUserId())));
    }

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<InventoryDemoBulkAdjustmentResponse>> applyAll(
            @Valid @RequestBody InventoryDemoBulkAdjustmentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                service.applyAll(request, principal == null ? null : principal.getUserId())
        ));
    }
}
