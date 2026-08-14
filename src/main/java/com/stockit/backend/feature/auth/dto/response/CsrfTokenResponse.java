package com.stockit.backend.feature.auth.dto.response;

public record CsrfTokenResponse(String token, String headerName) {
}
