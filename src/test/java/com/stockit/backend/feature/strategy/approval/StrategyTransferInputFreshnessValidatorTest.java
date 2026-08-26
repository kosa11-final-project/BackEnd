package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.calculator.InventoryTransferCostCalculator;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSkuVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferCostPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

class StrategyTransferInputFreshnessValidatorTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 27);

    @Test
    void acceptsTransferWhenCurrentInputsMatchGenerationSnapshot() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10.000", "2.000000", "500.000");

        validator(mapper).validate(resolved(transferCandidate()));
    }

    @Test
    void rejectsTransferWhenRouteDistanceChanged() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "11", "2", "500");

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "이동 경로가 변경"
        );
    }

    @Test
    void rejectsTransferWhenRouteIsNoLongerActive() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "500");
        when(mapper.selectActiveTransferRoutesByIds(List.of(List.of(900L))))
                .thenReturn(List.of());

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "이동 경로가 비활성화"
        );
    }

    @Test
    void rejectsTransferWhenRoutePhysicalDestinationChanged() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "500");
        StrategyCalculationTransferRouteVO changedRoute = route("10");
        changedRoute.setDestinationWarehouseId(503L);
        when(mapper.selectActiveTransferRoutesByIds(List.of(List.of(900L))))
                .thenReturn(List.of(changedRoute));

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "이동 경로가 변경"
        );
    }

    @Test
    void rejectsTransferWhenStoredCostDoesNotMatchCurrentCalculation() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "500");

        assertConflict(
                () -> validator(mapper).validate(resolved(
                        transferCandidateWithEstimatedCost("99")
                )),
                "현재 계산 결과가 일치하지 않"
        );
    }

    @Test
    void rejectsTransferWhenCostPolicyChanged() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "3", "500");

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "이동비 정책이 변경"
        );
    }

    @Test
    void rejectsTransferWhenMultiplePoliciesApply() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "500");
        when(mapper.selectTransferCostPolicies(START_DATE, START_DATE))
                .thenReturn(List.of(policy(1L, "2"), policy(2L, "2")));

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "하나로 확정"
        );
    }

    @Test
    void rejectsTransferWhenSkuWeightChanged() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "600");

        assertConflict(
                () -> validator(mapper).validate(resolved(transferCandidate())),
                "SKU 중량 정보가 변경"
        );
    }

    @Test
    void skipsTransferMasterQueriesForReallocation() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );

        validator(mapper).validate(resolved(reallocationCandidate()));

        verifyNoInteractions(mapper);
    }

    @Test
    void partitionsRouteIdsToStayBelowOracleInLimit() {
        List<Long> routeIds = java.util.stream.LongStream.rangeClosed(1, 1001)
                .boxed()
                .toList();

        assertThat(StrategyTransferInputFreshnessValidator.partitionRouteIds(routeIds))
                .satisfies(chunks -> {
                    assertThat(chunks).hasSize(2);
                    assertThat(chunks.get(0)).hasSize(900);
                    assertThat(chunks.get(1)).hasSize(101);
                    assertThat(chunks.stream().flatMap(List::stream).toList())
                            .containsExactlyElementsOf(routeIds);
                });
    }

    @Test
    void propagatesUnexpectedRuntimeDefectInsteadOfMaskingItAsConflict() {
        StrategyCalculationInputMapper mapper = mock(
                StrategyCalculationInputMapper.class
        );
        stubCurrentInputs(mapper, "10", "2", "500");
        InventoryTransferCostCalculator calculator = mock(
                InventoryTransferCostCalculator.class
        );
        when(calculator.unitWeightKg(any(), any()))
                .thenThrow(new NullPointerException("unexpected defect"));

        assertThatThrownBy(() -> validator(mapper, calculator)
                .validate(resolved(transferCandidate())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("unexpected defect");
    }

    private static StrategyTransferInputFreshnessValidator validator(
            StrategyCalculationInputMapper mapper
    ) {
        return validator(mapper, new InventoryTransferCostCalculator());
    }

    private static StrategyTransferInputFreshnessValidator validator(
            StrategyCalculationInputMapper mapper,
            InventoryTransferCostCalculator calculator
    ) {
        return new StrategyTransferInputFreshnessValidator(mapper, calculator);
    }

    private static void stubCurrentInputs(
            StrategyCalculationInputMapper mapper,
            String distance,
            String rate,
            String netWeight
    ) {
        StrategyCalculationSkuVO sku = new StrategyCalculationSkuVO();
        sku.setSkuId(100L);
        sku.setNetWeight(decimal(netWeight));
        sku.setWeightUnit("G");
        when(mapper.selectActiveSku(100L)).thenReturn(sku);
        when(mapper.selectActiveTransferRoutesByIds(List.of(List.of(900L))))
                .thenReturn(List.of(route(distance)));
        when(mapper.selectTransferCostPolicies(START_DATE, START_DATE))
                .thenReturn(List.of(policy(700L, rate)));
    }

    private static ResolvedStrategySelection resolved(
            StrategyGenerationResult.Candidate candidate
    ) {
        ResolvedStrategySelection resolved = mock(ResolvedStrategySelection.class);
        StrategyCalculationContext context = mock(StrategyCalculationContext.class);
        StrategyCalculationContext.Sku sku = new StrategyCalculationContext.Sku(
                100L, "SKU-100", "테스트 상품", "EA", BigDecimal.ONE,
                decimal("500"), "G"
        );
        StrategyGenerationResult.Option option = mock(
                StrategyGenerationResult.Option.class
        );
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        when(option.candidate()).thenReturn(candidate);
        when(context.sku()).thenReturn(sku);
        return resolved;
    }

    private static StrategyGenerationResult.Candidate transferCandidate() {
        return transferCandidateWithEstimatedCost("100");
    }

    private static StrategyGenerationResult.Candidate transferCandidateWithEstimatedCost(
            String estimatedCost
    ) {
        StrategyGenerationResult.MovementCost movement =
                new StrategyGenerationResult.MovementCost(
                        900L,
                        700L,
                        decimal("5.000000"),
                        decimal("10.000"),
                        decimal("2.000000"),
                        decimal(estimatedCost)
                );
        StrategyGenerationResult.Action action = new StrategyGenerationResult.Action(
                StrategyType.RT_TRANSFER,
                501L,
                10L,
                502L,
                20L,
                decimal("10"),
                decimal(estimatedCost),
                null,
                null,
                List.of(new StrategyGenerationResult.LotAllocation(
                        1L, 1001L, decimal("10"), 1
                )),
                movement
        );
        return candidate(action, StrategyType.RT_TRANSFER);
    }

    private static StrategyGenerationResult.Candidate reallocationCandidate() {
        StrategyGenerationResult.Action action = new StrategyGenerationResult.Action(
                StrategyType.REALLOCATION,
                501L,
                10L,
                501L,
                20L,
                decimal("10"),
                BigDecimal.ZERO,
                null,
                null,
                List.of(new StrategyGenerationResult.LotAllocation(
                        1L, 1001L, decimal("10"), 1
                ))
        );
        return candidate(action, StrategyType.REALLOCATION);
    }

    private static StrategyGenerationResult.Candidate candidate(
            StrategyGenerationResult.Action action,
            StrategyType type
    ) {
        return new StrategyGenerationResult.Candidate(
                "CAND-1",
                List.of(type),
                START_DATE,
                null,
                List.of(action),
                List.of(),
                new StrategyGenerationResult.Preference(1, 1, 100),
                decimal("10")
        );
    }

    private static StrategyCalculationTransferRouteVO route(String distance) {
        StrategyCalculationTransferRouteVO route =
                new StrategyCalculationTransferRouteVO();
        route.setTransferRouteId(900L);
        route.setSourceWarehouseId(501L);
        route.setDestinationWarehouseId(502L);
        route.setDistanceKm(decimal(distance));
        route.setDistanceSource("DUMMY");
        return route;
    }

    private static StrategyCalculationTransferCostPolicyVO policy(
            Long id, String rate
    ) {
        StrategyCalculationTransferCostPolicyVO policy =
                new StrategyCalculationTransferCostPolicyVO();
        policy.setTransferCostPolicyId(id);
        policy.setPolicyCode("GLOBAL-" + id);
        policy.setCostPerKgKm(decimal(rate));
        policy.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return policy;
    }

    private static void assertConflict(Runnable action, String messagePart) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                AppException.class,
                exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            ErrorCode.AI_STRATEGY_SELECTION_CONFLICT
                    );
                    assertThat(exception.getMessage()).contains(messagePart);
                }
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
