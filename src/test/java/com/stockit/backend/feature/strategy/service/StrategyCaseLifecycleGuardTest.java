package com.stockit.backend.feature.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseDetailMapper;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;

@ExtendWith(MockitoExtension.class)
class StrategyCaseLifecycleGuardTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 25, 10, 0);

    @Mock private AiStrategyCaseDetailMapper detailMapper;
    @Mock private StrategyDateTimeProvider dateTimeProvider;

    private StrategyCaseLifecycleGuard guard;

    @BeforeEach
    void setUp() {
        guard = new StrategyCaseLifecycleGuard(detailMapper, dateTimeProvider);
    }

    @Test
    void allowsOnlyGeneratedComparisonReadyCaseWithLiveResult() {
        AiStrategyCaseDetailVO strategyCase = generatedCase(NOW.plusDays(1));
        when(detailMapper.selectCaseDetail(123L)).thenReturn(strategyCase);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThat(guard.requireAdjustable(123L)).isSameAs(strategyCase);
    }

    @Test
    void allowsReadyCaseForIdempotentFinalSelectionWhileResultIsLive() {
        AiStrategyCaseDetailVO strategyCase = generatedCase(NOW.plusDays(1));
        strategyCase.setCaseStatus(StrategyCaseStatus.READY_TO_EXECUTE);
        when(detailMapper.selectCaseDetail(123L)).thenReturn(strategyCase);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThat(guard.requireSelectable(123L)).isSameAs(strategyCase);
    }

    @Test
    void rejectsCaseWhoseGenerationIsStillInProgress() {
        AiStrategyCaseDetailVO strategyCase = generatedCase(NOW.plusDays(1));
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setGenerationStage(StrategyGenerationStage.FORECASTING);
        when(detailMapper.selectCaseDetail(123L)).thenReturn(strategyCase);

        assertThatThrownBy(() -> guard.requireAdjustable(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_CASE_NOT_READY)
                );
    }

    @Test
    void rejectsCaseAtExactExpiryTime() {
        AiStrategyCaseDetailVO strategyCase = generatedCase(NOW);
        when(detailMapper.selectCaseDetail(123L)).thenReturn(strategyCase);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThatThrownBy(() -> guard.requireAdjustable(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
                );
    }

    private static AiStrategyCaseDetailVO generatedCase(
            LocalDateTime resultExpiresAt
    ) {
        AiStrategyCaseDetailVO value = new AiStrategyCaseDetailVO();
        value.setStrategyCaseId(123L);
        value.setCaseStatus(StrategyCaseStatus.GENERATED);
        value.setGenerationStage(StrategyGenerationStage.COMPARISON_READY);
        value.setResultCacheKey("ai-strategy:case:123:result:v1");
        value.setResultExpiresAt(resultExpiresAt);
        return value;
    }
}
