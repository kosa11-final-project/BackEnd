package com.stockit.backend.feature.strategy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.AiStrategyFailureAlertVO;

/** 최종 실패 AI 전략의 Teams 운영 알림 표시 정보를 조회한다. */
@Mapper
public interface AiStrategyFailureAlertMapper {

    AiStrategyFailureAlertVO selectFailureAlert(
            @Param("strategyCaseId") Long strategyCaseId
    );
}
