package com.stockit.backend.feature.strategy.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyGenerationResultResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyGenerationResultResponse.OptionPeriodPresentation;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyChartRangeResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyPeriodConstraintsResponse;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseDetailMapper;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.service.StrategyCasePayloadException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyLotDisplayVO;
import com.stockit.backend.feature.strategy.vo.AiStrategySalesPointReferenceVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyWarehouseReferenceVO;

/**
 * DB의 Case·마스터 정보와 Redis 생성 결과를 조합하는 AI 전략 상세조회 서비스
 *
 * <p>생성 결과 스냅샷은 변경하지 않고 화면 표시 정보만 응답 DTO에서 보강</p>
 */
@Service
public class AiStrategyCaseQueryServiceImpl implements AiStrategyCaseQueryService {

    private static final int ORACLE_IN_EXPRESSION_LIMIT = 1_000;

    private final AiStrategyCaseDetailMapper detailMapper;
    private final StrategyResultStore resultStore;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final StrategyDateTimeProvider dateTimeProvider;
    private final StrategySimulationContextStore contextStore;
    private final StrategyPeriodEligibilityPolicy periodEligibilityPolicy;

    public AiStrategyCaseQueryServiceImpl(
            AiStrategyCaseDetailMapper detailMapper,
            StrategyResultStore resultStore,
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            StrategyDateTimeProvider dateTimeProvider,
            StrategySimulationContextStore contextStore,
            StrategyPeriodEligibilityPolicy periodEligibilityPolicy
    ) {
        this.detailMapper = detailMapper;
        this.resultStore = resultStore;
        this.payloadSerializer = payloadSerializer;
        this.dateTimeProvider = dateTimeProvider;
        this.contextStore = contextStore;
        this.periodEligibilityPolicy = periodEligibilityPolicy;
    }

    /**
     * Case 상태와 요청 조건, 생성 결과를 조회하고 화면 표시용 참조 정보를 일괄 조합
     *
     * @param strategyCaseId 조회할 AI 전략 Case 식별자
     * @return 화면 표시 정보가 보강된 상세 응답
     */
    @Override
    @Transactional(readOnly = true)
    public AiStrategyCaseResponse find(Long strategyCaseId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        AiStrategyCaseDetailVO strategyCase = detailMapper.selectCaseDetail(
                strategyCaseId
        );
        if (strategyCase == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }

        StrategyGenerationResult result = loadResult(strategyCase);
        StrategyCaseRequestPayload payload = deserializePayload(strategyCase);
        StrategyCalculationContext context = result == null
                ? null
                : loadContext(strategyCase.getStrategyCaseId());
        ReferenceIds referenceIds = collectReferenceIds(strategyCase, payload, result);

        Map<Long, AiStrategySalesPointReferenceVO> salesPoints = salesPoints(
                referenceIds.salesPointIds()
        );
        Map<Long, AiStrategyWarehouseReferenceVO> warehouses = warehouses(
                referenceIds.warehouseIds()
        );
        Map<Long, AiStrategyLotDisplayVO> lots = lots(referenceIds.lotIds());

        AiStrategyGenerationResultResponse resultResponse = resultResponse(
                result,
                payload,
                context,
                salesPoints,
                warehouses,
                lots
        );
        return AiStrategyCaseResponse.from(
                strategyCase, payload, salesPoints, lots, resultResponse
        );
    }

    private StrategyCalculationContext loadContext(Long strategyCaseId) {
        try {
            return contextStore.find(strategyCaseId).orElseThrow(() ->
                    new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED));
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    private AiStrategyGenerationResultResponse resultResponse(
            StrategyGenerationResult result,
            StrategyCaseRequestPayload payload,
            StrategyCalculationContext context,
            Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
            Map<Long, AiStrategyWarehouseReferenceVO> warehouses,
            Map<Long, AiStrategyLotDisplayVO> lots
    ) {
        try {
            return AiStrategyGenerationResultResponse.from(
                    result,
                    salesPoints,
                    warehouses,
                    lots,
                    periodPresentations(result, payload, context)
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (AppException exception) {
            if (exception.getErrorCode() == ErrorCode.AI_STRATEGY_RESULT_EXPIRED) {
                throw exception;
            }
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        }
    }

    private Map<String, OptionPeriodPresentation> periodPresentations(
            StrategyGenerationResult result,
            StrategyCaseRequestPayload payload,
            StrategyCalculationContext context
    ) {
        if (result == null) {
            return Map.of();
        }
        return result.options().stream().collect(Collectors.toUnmodifiableMap(
                option -> option.candidate().candidateId(),
                option -> {
                    StrategyGenerationResult.Candidate candidate = option.candidate();
                    LocalDate endDate = candidate.endDate() == null
                            ? payload.forecastEndDate()
                            : candidate.endDate();
                    List<Long> allocatedIds = candidate.actions().stream()
                            .flatMap(action -> action.lotAllocations().stream())
                            .map(StrategyGenerationResult.LotAllocation::inventoryBalanceId)
                            .distinct()
                            .toList();
                    return new OptionPeriodPresentation(
                            AiStrategyPeriodConstraintsResponse.from(
                                    periodEligibilityPolicy.constraints(
                                            context,
                                            candidate.startDate(),
                                            endDate,
                                            allocatedIds,
                                            dateTimeProvider.now().toLocalDate()
                                    )
                            ),
                            new AiStrategyChartRangeResponse(
                                    candidate.startDate(),
                                    endDate
                            )
                    );
                }
        ));
    }

    private StrategyCaseRequestPayload deserializePayload(
            AiStrategyCaseDetailVO strategyCase
    ) {
        try {
            return payloadSerializer.deserialize(strategyCase.getRequestPayloadJson());
        } catch (StrategyCasePayloadException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DB 상태와 만료시각을 기준으로 Redis 결과의 조회 가능 여부 판정
     *
     * <p>{@code GENERATED}인데 Redis 결과가 없으면 생성 중이 아닌 만료로 해석</p>
     */
    private StrategyGenerationResult loadResult(AiStrategyCaseDetailVO strategyCase) {
        boolean generated = strategyCase.getCaseStatus() == StrategyCaseStatus.GENERATED;
        if (strategyCase.getCaseStatus() == StrategyCaseStatus.EXPIRED
                || (generated && (strategyCase.getResultExpiresAt() == null
                || !strategyCase.getResultExpiresAt().isAfter(dateTimeProvider.now())))) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        }
        if (strategyCase.getResultCacheKey() == null) {
            if (generated) {
                throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
            }
            return null;
        }
        try {
            StrategyGenerationResult result = resultStore.find(
                    strategyCase.getStrategyCaseId()
            ).orElse(null);
            if (generated && result == null) {
                throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
            }
            return result;
        } catch (InvalidStrategyResultException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RESULT_EXPIRED);
        } catch (StrategyResultStoreException exception) {
            throw exception;
        }
    }

    /** 요청 조건과 추천 액션에 흩어진 표시명 조회 대상을 중복 없이 통합 */
    private static ReferenceIds collectReferenceIds(
            AiStrategyCaseDetailVO strategyCase,
            StrategyCaseRequestPayload payload,
            StrategyGenerationResult result
    ) {
        Set<Long> salesPointIds = new LinkedHashSet<>();
        Set<Long> warehouseIds = new LinkedHashSet<>();
        Set<Long> lotIds = new LinkedHashSet<>(payload.lotIds());
        add(salesPointIds, strategyCase.getRequestedSalesPointId());
        salesPointIds.addAll(payload.candidateSalesPointIds());

        if (result != null) {
            result.options().stream()
                    .flatMap(option -> option.candidate().actions().stream())
                    .forEach(action -> {
                        add(salesPointIds, action.sourceSalesPointId());
                        add(salesPointIds, action.targetSalesPointId());
                        add(warehouseIds, action.sourceWarehouseId());
                        add(warehouseIds, action.targetWarehouseId());
                        action.lotAllocations().forEach(allocation ->
                                add(lotIds, allocation.lotId())
                        );
                    });
        }
        return new ReferenceIds(
                List.copyOf(salesPointIds),
                List.copyOf(warehouseIds),
                List.copyOf(lotIds)
        );
    }

    private Map<Long, AiStrategySalesPointReferenceVO> salesPoints(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectInBatches(ids, detailMapper::selectSalesPoints).stream()
                .collect(Collectors.toMap(
                        AiStrategySalesPointReferenceVO::getSalesPointId,
                        Function.identity()
                ));
    }

    private Map<Long, AiStrategyWarehouseReferenceVO> warehouses(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectInBatches(ids, detailMapper::selectWarehouses).stream()
                .collect(Collectors.toMap(
                        AiStrategyWarehouseReferenceVO::getWarehouseId,
                        Function.identity()
                ));
    }

    private Map<Long, AiStrategyLotDisplayVO> lots(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectInBatches(ids, detailMapper::selectLots).stream()
                .collect(Collectors.toMap(
                        AiStrategyLotDisplayVO::getLotId,
                        Function.identity()
                ));
    }

    /**
     * 개수 제한이 없는 LOT 요청도 처리하기 위한 Oracle IN 절 1,000개 단위 분할 조회
     */
    private static <T> List<T> selectInBatches(
            List<Long> ids,
            Function<List<Long>, List<T>> query
    ) {
        List<T> result = new ArrayList<>();
        for (int fromIndex = 0;
                fromIndex < ids.size();
                fromIndex += ORACLE_IN_EXPRESSION_LIMIT) {
            int toIndex = Math.min(
                    fromIndex + ORACLE_IN_EXPRESSION_LIMIT,
                    ids.size()
            );
            result.addAll(query.apply(List.copyOf(ids.subList(fromIndex, toIndex))));
        }
        return List.copyOf(result);
    }

    private static void add(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private record ReferenceIds(
            List<Long> salesPointIds,
            List<Long> warehouseIds,
            List<Long> lotIds
    ) {
    }
}
