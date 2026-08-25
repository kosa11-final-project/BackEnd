package com.stockit.backend.feature.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.stockit.backend.feature.auth.mapper.AuthMapper;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.auth.vo.AuthUserVO;

/**
 * 활성 상태이며 삭제되지 않고 GREENFOOD_ADMIN 역할이 할당된 사용자를 조회하는 서비스
 *
 * <p>계정 존재 여부, 비활성 상태, 역할 미할당 여부가 외부에 노출되지 않도록
 * 모든 로그인 불가 상태를 동일한 인증 실패로 처리함</p>
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthMapper authMapper;

    public AuthUserDetailsService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        AuthUserVO user = authMapper.selectActiveAdminByLoginId(loginId);
        if (user == null) {
            throw new UsernameNotFoundException("Authentication failed");
        }
        return AuthPrincipal.from(user);
    }
}
