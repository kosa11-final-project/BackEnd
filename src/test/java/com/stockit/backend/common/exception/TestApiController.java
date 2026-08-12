package com.stockit.backend.common.exception;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/test")
class TestApiController {

    @GetMapping("/success")
    ApiResponse<Map<String, String>> success() {
        return ApiResponse.of(Map.of("name", "stockit"));
    }

    @PostMapping("/validate")
    ApiResponse<TestRequest> validate(@Valid @RequestBody TestRequest request) {
        return ApiResponse.of(request);
    }

    @GetMapping("/app-error")
    void appError() {
        throw new AppException(ErrorCode.TMP_CONFLICT);
    }

    @GetMapping("/unexpected-error")
    void unexpectedError() {
        throw new IllegalStateException("외부에 노출하면 안 되는 메시지");
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }
}
