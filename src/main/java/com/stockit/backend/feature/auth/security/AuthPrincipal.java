package com.stockit.backend.feature.auth.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.stockit.backend.feature.auth.vo.AuthUserVO;

/**
 * HTTP 세션에 저장되는 최소 인증 사용자 정보
 *
 * <p>비밀번호 해시는 직렬화 대상에서 제외하고 인증 직후 제거하며, 로그인할 때마다
 * 새로운 객체가 생성되어도 동일 사용자의 중복 로그인을 식별할 수 있도록
 * {@code userId}를 기준으로 동일성을 판단함</p>
 */
public final class AuthPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String loginId;
    private transient String passwordHash;
    private final String userName;
    private final String email;
    private final Long organizationId;
    private final String organizationName;
    private final String roleCode;

    private AuthPrincipal(AuthUserVO user) {
        this.userId = user.getUserId();
        this.loginId = user.getLoginId();
        this.passwordHash = user.getPasswordHash();
        this.userName = user.getUserName();
        this.email = user.getEmail();
        this.organizationId = user.getOrganizationId();
        this.organizationName = user.getOrganizationName();
        this.roleCode = user.getRoleCode();
    }

    public static AuthPrincipal from(AuthUserVO user) {
        return new AuthPrincipal(user);
    }

    public Long getUserId() {
        return userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public void eraseCredentials() {
        // 인증이 끝난 뒤 세션에 비밀번호 해시가 남지 않도록 참조 제거
        passwordHash = null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AuthPrincipal principal)) {
            return false;
        }
        return Objects.equals(userId, principal.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
