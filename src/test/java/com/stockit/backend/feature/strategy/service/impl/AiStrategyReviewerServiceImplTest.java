package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.mapper.AiStrategyReviewerMapper;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyReviewerServiceImplTest {

    @Mock private AiStrategyReviewerMapper reviewerMapper;
    private AiStrategyReviewerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStrategyReviewerServiceImpl(reviewerMapper);
    }

    @Test
    void returnsAvailableReviewersInCurrentOrganization() {
        AiStrategyReviewerVO reviewer = reviewer(7L, "reviewer@stockit.test");
        when(reviewerMapper.selectAvailableReviewers(1L))
                .thenReturn(List.of(reviewer));

        var response = service.findAll(1L);

        assertThat(response.reviewers()).hasSize(1);
        assertThat(response.reviewers().get(0).reviewerId()).isEqualTo(7L);
        assertThat(response.reviewers().get(0).email())
                .isEqualTo("reviewer@stockit.test");
    }

    @Test
    void rejectsDuplicateReviewerIdsBeforeQuery() {
        assertThatThrownBy(() -> service.requireReviewers(
                1L, List.of(7L, 7L), 10
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_INVALID_REVIEWERS);
    }

    @Test
    void rejectsReviewerOutsideCurrentOrganization() {
        when(reviewerMapper.selectAvailableReviewersByIds(1L, List.of(7L, 8L)))
                .thenReturn(List.of(reviewer(7L, "one@stockit.test")));

        assertThatThrownBy(() -> service.requireReviewers(
                1L, List.of(7L, 8L), 10
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_REVIEWER_NOT_FOUND);
    }

    @Test
    void rejectsDifferentUsersWithSameNormalizedEmail() {
        when(reviewerMapper.selectAvailableReviewersByIds(1L, List.of(7L, 8L)))
                .thenReturn(List.of(
                        reviewer(7L, "Reviewer@StockIt.test"),
                        reviewer(8L, " reviewer@stockit.test ")
                ));

        assertThatThrownBy(() -> service.requireReviewers(
                1L, List.of(7L, 8L), 10
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_INVALID_REVIEWERS);
    }

    private static AiStrategyReviewerVO reviewer(Long id, String email) {
        AiStrategyReviewerVO reviewer = new AiStrategyReviewerVO();
        reviewer.setReviewerId(id);
        reviewer.setReviewerName("검토자 " + id);
        reviewer.setEmail(email);
        reviewer.setOrganizationId(1L);
        reviewer.setOrganizationName("StockIt");
        reviewer.setRoleName("그린푸드 총괄");
        return reviewer;
    }
}
