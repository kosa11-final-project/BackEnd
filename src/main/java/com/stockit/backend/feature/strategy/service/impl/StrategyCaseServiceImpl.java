package com.stockit.backend.feature.strategy.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.ForecastDateRange;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationRequestedEvent;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.LegacyStrategyCaseCodeGenerator;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.service.StrategyForecastDateRangeResolver;
import com.stockit.backend.feature.strategy.service.StrategySalesPointQuerySupport;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;
import com.stockit.backend.feature.strategy.vo.StrategySkuReferenceVO;

/**
 * AI 전략 생성 요청의 도메인 검증과 최초 영속화를 담당하는 서비스
 *
 * <p>비동기 Worker가 사용자 요청과 무관하게 작업을 복원할 수 있도록 선택 조건과
 * 확정 예측 기간을 Case에 먼저 저장한 뒤, DB 식별자와 생성 시각을 담은 내부 이벤트를 발행</p>
 */
@Service
public class StrategyCaseServiceImpl implements StrategyCaseService {

    private static final int MAX_CASE_NAME_LENGTH = 200;
    private static final String AVAILABLE_LOT_STATUS = "AVAILABLE";

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyForecastDateRangeResolver dateRangeResolver;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final LegacyStrategyCaseCodeGenerator caseCodeGenerator;
    private final StrategyDateTimeProvider dateTimeProvider;
    private final ApplicationEventPublisher eventPublisher;

    public StrategyCaseServiceImpl(
            StrategyCaseMapper strategyCaseMapper,
            StrategyForecastDateRangeResolver dateRangeResolver,
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            LegacyStrategyCaseCodeGenerator caseCodeGenerator,
            StrategyDateTimeProvider dateTimeProvider,
            ApplicationEventPublisher eventPublisher
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.dateRangeResolver = dateRangeResolver;
        this.payloadSerializer = payloadSerializer;
        this.caseCodeGenerator = caseCodeGenerator;
        this.dateTimeProvider = dateTimeProvider;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 참조 무결성과 사용자 우선순위를 검증하고 비동기 처리의 복구 기준이 될 Case 생성
     */
    @Override
    @Transactional
    public StrategyCaseCreated createStrategyCase(
            CreateStrategyCaseCommand command,
            Long requesterId
    ) {
        validateRequiredValues(command, requesterId);
        validateDuplicates(command);
        validateStrategyTypes(command.strategyTypes());

        StrategySkuReferenceVO sku = strategyCaseMapper.selectActiveSku(command.skuId());
        if (sku == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_SKU_NOT_FOUND);
        }

        validateSalesPoints(command.sourceSalesPointId(), command.candidateSalesPointIds());
        validateLots(command.skuId(), command.lotIds());

        // 날짜 검증과 생성 이벤트의 기준 시각이 자정 경계를 사이에 두고 달라지지 않도록 한 번만 고정
        LocalDateTime requestedAt = dateTimeProvider.now();
        ForecastDateRange forecastDateRange = dateRangeResolver.resolve(
                command.preferredStartDate(),
                command.preferredEndDate(),
                requestedAt.toLocalDate()
        );

        String caseName = resolveCaseName(command.caseName(), sku.getSkuName());
        String requestPayloadJson = payloadSerializer.serialize(new StrategyCaseRequestPayload(
                command.lotIds(),
                command.candidateSalesPointIds(),
                command.strategyTypes(),
                command.preferredStartDate(),
                command.preferredEndDate(),
                forecastDateRange.startDate(),
                forecastDateRange.endDate()
        ));

        StrategyCaseVO strategyCase = StrategyCaseVO.generating(
                command.skuId(),
                command.sourceSalesPointId(),
                // 기존 NOT NULL 컬럼 호환용 값이며 외부 전략 식별자로 사용하지 않음
                caseCodeGenerator.generate(),
                caseName,
                requestPayloadJson,
                requesterId
        );
        strategyCaseMapper.insertStrategyCase(strategyCase);

        // 메시지에는 DB 식별자와 생성 시각만 전달하므로 작업 발행보다 Case 영속화가 항상 선행
        if (strategyCase.getStrategyCaseId() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        // DB가 생성한 식별자와 감사 시각을 후속 처리에 동일하게 전달하기 위한 재조회
        StrategyCaseVO persisted = strategyCaseMapper.selectStrategyCaseById(
                strategyCase.getStrategyCaseId()
        );
        if (persisted == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        // 현재 트랜잭션이 커밋된 뒤 RabbitMQ로 전달하기 위한 내부 이벤트 발행
        eventPublisher.publishEvent(new StrategyGenerationRequestedEvent(
                persisted.getStrategyCaseId(),
                persisted.getCreatedAt()
        ));
        return new StrategyCaseCreated(
                persisted.getStrategyCaseId(),
                persisted.getCaseName(),
                persisted.getCaseStatus(),
                persisted.getGenerationStage(),
                persisted.getCreatedAt()
        );
    }

    private static void validateRequiredValues(
            CreateStrategyCaseCommand command,
            Long requesterId
    ) {
        if (command == null
                || !isPositive(command.skuId())
                || !isPositive(requesterId)
                || (command.sourceSalesPointId() != null
                && !isPositive(command.sourceSalesPointId()))
                || containsInvalidId(command.lotIds())
                || containsInvalidId(command.candidateSalesPointIds())
                || command.strategyTypes().stream().anyMatch(Objects::isNull)) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REQUEST);
        }
        if (command.caseName() != null
                && !command.caseName().isBlank()
                && command.caseName().trim().length() > MAX_CASE_NAME_LENGTH) {
            throw new AppException(ErrorCode.AI_STRATEGY_INVALID_REQUEST);
        }
    }

    private static void validateDuplicates(CreateStrategyCaseCommand command) {
        if (containsDuplicate(command.lotIds())
                || containsDuplicate(command.candidateSalesPointIds())
                || containsDuplicate(command.strategyTypes())) {
            throw new AppException(ErrorCode.AI_STRATEGY_DUPLICATE_INPUT);
        }
    }

    private static void validateStrategyTypes(List<StrategyType> strategyTypes) {
        boolean containsUnsupportedType = strategyTypes.stream()
                .anyMatch(strategyType -> !strategyType.isSupportedForGeneration());
        if (containsUnsupportedType) {
            throw new AppException(ErrorCode.AI_STRATEGY_UNSUPPORTED_TYPE);
        }
    }

    private void validateSalesPoints(
            Long sourceSalesPointId,
            List<Long> candidateSalesPointIds
    ) {
        Set<Long> requestedIds = new LinkedHashSet<>(candidateSalesPointIds);
        if (sourceSalesPointId != null) {
            // 현재 판매처 유지 전략을 위해 후보 목록과의 중복은 허용하되 조회 대상만 통합
            requestedIds.add(sourceSalesPointId);
        }
        if (requestedIds.isEmpty()) {
            return;
        }

        List<Long> activeIds = StrategySalesPointQuerySupport.selectActiveSalesPointIds(
                strategyCaseMapper,
                List.copyOf(requestedIds)
        );
        if (activeIds.size() != requestedIds.size()
                || !new HashSet<>(activeIds).containsAll(requestedIds)) {
            throw new AppException(ErrorCode.AI_STRATEGY_SALES_POINT_NOT_FOUND);
        }
    }

    private void validateLots(Long skuId, List<Long> lotIds) {
        if (lotIds.isEmpty()) {
            return;
        }

        Map<Long, StrategyLotReferenceVO> referencesById = strategyCaseMapper
                .selectLotReferences(lotIds)
                .stream()
                .collect(Collectors.toMap(
                        StrategyLotReferenceVO::getLotId,
                        Function.identity()
                ));
        if (referencesById.size() != lotIds.size()) {
            throw new AppException(ErrorCode.AI_STRATEGY_LOT_NOT_FOUND);
        }

        for (Long lotId : lotIds) {
            StrategyLotReferenceVO lot = referencesById.get(lotId);
            if (!skuId.equals(lot.getSkuId())) {
                throw new AppException(ErrorCode.AI_STRATEGY_LOT_NOT_BELONG_TO_SKU);
            }
            if (!AVAILABLE_LOT_STATUS.equals(lot.getLotStatus())) {
                throw new AppException(ErrorCode.AI_STRATEGY_LOT_NOT_FOUND);
            }
        }
    }

    private static String resolveCaseName(
            String requestedCaseName,
            String skuName
    ) {
        if (requestedCaseName != null && !requestedCaseName.isBlank()) {
            return requestedCaseName.trim();
        }

        String suffix = " AI 전략";
        int maxSkuNameLength = MAX_CASE_NAME_LENGTH - suffix.length();
        String normalizedSkuName = skuName == null ? "SKU" : skuName.trim();
        if (normalizedSkuName.length() > maxSkuNameLength) {
            normalizedSkuName = normalizedSkuName.substring(0, maxSkuNameLength);
        }
        return normalizedSkuName + suffix;
    }

    private static boolean containsInvalidId(List<Long> ids) {
        return ids.stream().anyMatch(id -> !isPositive(id));
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static boolean containsDuplicate(List<?> values) {
        return new HashSet<>(values).size() != values.size();
    }
}
