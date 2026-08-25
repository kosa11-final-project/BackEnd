package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/strategy-case-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyCaseMapperTest {

    @Autowired
    private StrategyCaseMapper strategyCaseMapper;

    @Test
    void readsActiveReferencesInBulk() {
        assertThat(strategyCaseMapper.selectActiveSku(101L).getSkuName())
                .isEqualTo("테스트 SKU");
        assertThat(strategyCaseMapper.selectActiveSku(102L)).isNull();
        assertThat(strategyCaseMapper.selectActiveSalesPointIds(List.of(10L, 20L, 30L)))
                .containsExactlyInAnyOrder(10L, 20L);
        assertThat(strategyCaseMapper.selectActiveSalesPointIdsBySkuInventory(101L))
                .containsExactly(10L, 20L);
        assertThat(strategyCaseMapper.selectActiveSalesPointIdsBySkuInventory(102L))
                .isEmpty();

        List<StrategyLotReferenceVO> lots = strategyCaseMapper.selectLotReferences(
                List.of(1001L, 1002L, 1003L)
        );
        assertThat(lots).hasSize(3);
        assertThat(lots).filteredOn(lot -> lot.getLotId().equals(1001L))
                .singleElement()
                .satisfies(lot -> {
                    assertThat(lot.getSkuId()).isEqualTo(101L);
                    assertThat(lot.getLotStatus()).isEqualTo("AVAILABLE");
                    assertThat(lot.getWarehouseId()).isEqualTo(501L);
                });
    }

    @Test
    void insertsAndReadsStrategyCaseWithGeneratedIdentity() {
        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                101L,
                10L,
                "SC-0123456789abcdef0123456789abcdef",
                "테스트 전략",
                "{\"lotIds\":[1001]}",
                99L
        );

        strategyCaseMapper.insertStrategyCase(strategyCase);

        assertThat(strategyCase.getStrategyCaseId()).isNotNull();
        StrategyCaseVO saved = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        assertThat(saved.getCaseStatus()).isEqualTo(StrategyCaseStatus.GENERATING);
        assertThat(saved.getGenerationStage()).isNull();
        assertThat(saved.getCaseName()).isEqualTo("테스트 전략");
        assertThat(saved.getRequestPayloadJson()).contains("1001");
        assertThat(saved.getCreatedBy()).isEqualTo(99L);
        assertThat(saved.getUpdatedBy()).isEqualTo(99L);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getIsDeleted()).isFalse();

        assertThat(strategyCaseMapper.markForecastingIfPending(
                strategyCase.getStrategyCaseId()
        )).isEqualTo(1);
        assertThat(strategyCaseMapper.markForecastingIfPending(
                strategyCase.getStrategyCaseId()
        )).isZero();
        assertThat(strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        ).getGenerationStage()).isEqualTo(StrategyGenerationStage.FORECASTING);

        assertThat(strategyCaseMapper.markStrategyGeneratingIfForecasting(
                strategyCase.getStrategyCaseId()
        )).isEqualTo(1);
        assertThat(strategyCaseMapper.markStrategyGeneratingIfForecasting(
                strategyCase.getStrategyCaseId()
        )).isZero();
        assertThat(strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        ).getGenerationStage()).isEqualTo(
                StrategyGenerationStage.STRATEGY_GENERATING
        );
    }

    @Test
    void recordsFailureOnlyWhileCaseIsGenerating() {
        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                101L,
                10L,
                "SC-0123456789abcdef0123456789abcdee",
                "실패 테스트 전략",
                "{\"lotIds\":[]}",
                99L
        );
        strategyCaseMapper.insertStrategyCase(strategyCase);

        assertThat(strategyCaseMapper.markGenerationFailedIfGenerating(
                strategyCase.getStrategyCaseId(),
                "MQ_RETRY_EXHAUSTED",
                "temporary failure"
        )).isEqualTo(1);
        assertThat(strategyCaseMapper.markGenerationFailedIfGenerating(
                strategyCase.getStrategyCaseId(),
                "MQ_RETRY_EXHAUSTED",
                "duplicated failure"
        )).isZero();

        StrategyCaseVO failed = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        assertThat(failed.getCaseStatus())
                .isEqualTo(StrategyCaseStatus.GENERATION_FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("MQ_RETRY_EXHAUSTED");
        assertThat(failed.getFailureMessage()).isEqualTo("temporary failure");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    void doesNotOverwriteForecastingCaseWithFailure() {
        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                101L,
                10L,
                "SC-0123456789abcdef0123456789abcdff",
                "수요예측 진행 전략",
                "{\"lotIds\":[]}",
                99L
        );
        strategyCaseMapper.insertStrategyCase(strategyCase);
        assertThat(strategyCaseMapper.markForecastingIfPending(
                strategyCase.getStrategyCaseId()
        )).isEqualTo(1);

        assertThat(strategyCaseMapper.markGenerationFailedIfGenerating(
                strategyCase.getStrategyCaseId(),
                "MQ_MESSAGE_INVALID",
                "duplicated invalid message"
        )).isZero();

        StrategyCaseVO forecasting = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        assertThat(forecasting.getCaseStatus())
                .isEqualTo(StrategyCaseStatus.GENERATING);
        assertThat(forecasting.getGenerationStage())
                .isEqualTo(StrategyGenerationStage.FORECASTING);
        assertThat(forecasting.getFailureCode()).isNull();
        assertThat(forecasting.getFailureMessage()).isNull();
        assertThat(forecasting.getCompletedAt()).isNull();
    }

    @Test
    void recordsFailureOnlyAtExpectedGenerationStage() {
        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                101L,
                10L,
                "SC-0123456789abcdef0123456789abcddd",
                "단계 실패 테스트 전략",
                "{\"lotIds\":[]}",
                99L
        );
        strategyCaseMapper.insertStrategyCase(strategyCase);
        assertThat(strategyCaseMapper.markForecastingIfPending(
                strategyCase.getStrategyCaseId()
        )).isEqualTo(1);

        assertThat(strategyCaseMapper.markGenerationFailedAtStage(
                strategyCase.getStrategyCaseId(),
                StrategyGenerationStage.STRATEGY_GENERATING,
                "WRONG_STAGE",
                "must not overwrite"
        )).isZero();
        assertThat(strategyCaseMapper.markGenerationFailedAtStage(
                strategyCase.getStrategyCaseId(),
                StrategyGenerationStage.FORECASTING,
                "FORECAST_UNAVAILABLE",
                "forecast unavailable"
        )).isEqualTo(1);

        StrategyCaseVO failed = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        assertThat(failed.getCaseStatus()).isEqualTo(
                StrategyCaseStatus.GENERATION_FAILED
        );
        assertThat(failed.getFailureCode()).isEqualTo("FORECAST_UNAVAILABLE");
    }
}
