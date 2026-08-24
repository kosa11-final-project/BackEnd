package com.stockit.backend.feature.strategy.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

/** 동일 조직 Reviewer 조회와 전송 대상 검증을 담당하는 Mapper. */
@Mapper
public interface AiStrategyReviewerMapper {

    List<AiStrategyReviewerVO> selectAvailableReviewers(
            @Param("organizationId") Long organizationId
    );

    List<AiStrategyReviewerVO> selectAvailableReviewersByIds(
            @Param("organizationId") Long organizationId,
            @Param("reviewerIds") List<Long> reviewerIds
    );
}
