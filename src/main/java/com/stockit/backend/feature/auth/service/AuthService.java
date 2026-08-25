package com.stockit.backend.feature.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.auth.mapper.AuthMapper;

/**
 * 인증 성공 후 필요한 사용자 이력을 갱신하는 서비스
 */
@Service
public class AuthService {

    private final AuthMapper authMapper;

    public AuthService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Transactional
    public void recordSuccessfulLogin(Long userId) {
        // 인증이 최종 성공한 경우에만 최근 로그인 시각과 감사 컬럼 갱신
        authMapper.updateLastLoginAt(userId);
    }
}
