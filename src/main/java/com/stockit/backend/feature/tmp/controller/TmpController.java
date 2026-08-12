package com.stockit.backend.feature.tmp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.tmp.dto.request.TmpEchoRequest;
import com.stockit.backend.feature.tmp.dto.response.TmpResponse;
import com.stockit.backend.feature.tmp.service.TmpService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tmp")
public class TmpController {

    private final TmpService tmpService;

    public TmpController(TmpService tmpService) {
        this.tmpService = tmpService;
    }

    @GetMapping("/ping")
    public ApiResponse<TmpResponse> ping() {
        return ApiResponse.of(TmpResponse.from(tmpService.getWelcomeMessage()));
    }

    @PostMapping("/echo")
    public ApiResponse<TmpResponse> echo(@Valid @RequestBody TmpEchoRequest request) {
        return ApiResponse.of(TmpResponse.from(tmpService.echo(request.message())));
    }
}
