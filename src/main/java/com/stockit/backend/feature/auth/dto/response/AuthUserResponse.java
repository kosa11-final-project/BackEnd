package com.stockit.backend.feature.auth.dto.response;

import com.stockit.backend.feature.auth.security.AuthPrincipal;

public record AuthUserResponse(
        Long userId,
        String loginId,
        String userName,
        String email,
        Long organizationId,
        String organizationName,
        String roleCode
) {

    public static AuthUserResponse from(AuthPrincipal principal) {
        return new AuthUserResponse(
                principal.getUserId(),
                principal.getLoginId(),
                principal.getUserName(),
                principal.getEmail(),
                principal.getOrganizationId(),
                principal.getOrganizationName(),
                principal.getRoleCode()
        );
    }
}
