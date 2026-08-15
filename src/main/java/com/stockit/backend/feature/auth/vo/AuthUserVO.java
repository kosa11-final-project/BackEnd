package com.stockit.backend.feature.auth.vo;

import com.stockit.backend.common.persistence.BaseEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthUserVO extends BaseEntity {

    private Long userId;
    private String loginId;
    private String passwordHash;
    private String userName;
    private String email;
    private Long organizationId;
    private String organizationName;
    private String roleCode;
}
