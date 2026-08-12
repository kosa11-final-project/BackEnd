package com.stockit.backend.feature.tmp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TmpEchoRequest(
        @NotBlank(message = "메시지는 필수입니다.") String message
) {
}
