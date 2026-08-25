package com.stockit.backend.feature.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.auth.vo.AuthUserVO;

@Mapper
public interface AuthMapper {

    AuthUserVO selectActiveAdminByLoginId(@Param("loginId") String loginId);

    int updateLastLoginAt(@Param("userId") Long userId);
}
