package com.stockit.backend.feature.strategy.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyRetryDateAdjustmentPolicy;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.dto.response.RetryAiStrategyGenerationResponse;
import com.stockit.backend.feature.strategy.dto.response.RetryConditionsStaleDetails;
import com.stockit.backend.feature.strategy.dto.response.RetryDateAdjustmentRequiredDetails;
import com.stockit.backend.feature.strategy.dto.response.RetryPeriodExpiredDetails;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.AiStrategyGenerationRetryService;
import com.stockit.backend.feature.strategy.service.StrategyCasePayloadException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;

/** 실패 이력을 보존하면서 동일 사용자 조건으로 신규 전략 생성 Case를 만든다. */
@Service
public class AiStrategyGenerationRetryServiceImpl
        implements AiStrategyGenerationRetryService {

    private static final String AVAILABLE_LOT_STATUS = "AVAILABLE";
    private static final String START_DATE_PASSED = "PREFERRED_START_DATE_PASSED";
    private static final String SELLABLE_PERIOD_EXCEEDED = "SELLABLE_PERIOD_EXCEEDED";

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final StrategyCaseService strategyCaseService;
    private final StrategyDateTimeProvider dateTimeProvider;

    public AiStrategyGenerationRetryServiceImpl(
            StrategyCaseMapper strategyCaseMapper,
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            StrategyCaseService strategyCaseService,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.payloadSerializer = payloadSerializer;
        this.strategyCaseService = strategyCaseService;
        this.dateTimeProvider = dateTimeProvider;
    }

    /**
     * 원본 실패 Case를 잠가 직접 자식 재시도를 하나로 제한하고, 신규 Case 생성은
     * 기존 AFTER_COMMIT RabbitMQ 발행 경로에 위임한다.
     */
    @Override
    @Transactional
    public RetryAiStrategyGenerationResponse retry(
            Long failedStrategyCaseId,
            StrategyRetryDateAdjustmentPolicy dateAdjustmentPolicy,
            Long requesterId
    ) {
        validateRequest(failedStrategyCaseId, requesterId);
        StrategyRetryDateAdjustmentPolicy effectivePolicy = dateAdjustmentPolicy == null
                ? StrategyRetryDateAdjustmentPolicy.REJECT
                : dateAdjustmentPolicy;

        StrategyCaseVO original = strategyCaseMapper.selectStrategyCaseByIdForUpdate(
                failedStrategyCaseId
        );
        if (original == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (original.getCaseStatus() != StrategyCaseStatus.GENERATION_FAILED) {
            throw new AppException(ErrorCode.AI_STRATEGY_RETRY_NOT_ALLOWED);
        }

        StrategyCaseVO existingRetry = strategyCaseMapper.selectRetryCaseByParentId(
                failedStrategyCaseId
        );
        if (existingRetry != null) {
            return RetryAiStrategyGenerationResponse.existing(
                    failedStrategyCaseId,
                    existingRetry,
                    existingDateAdjustment(original, existingRetry)
            );
        }

        StrategyCaseRequestPayload originalPayload = deserializePayload(
                original.getRequestPayloadJson()
        );
        validateStoredPayload(originalPayload);

        LocalDateTime requestedAt = dateTimeProvider.now();
        if (requestedAt == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        RetryDates retryDates = resolveRetryDates(
                originalPayload,
                requestedAt.toLocalDate(),
                effectivePolicy
        );
        validateSelectedLotSellablePeriod(
                original.getSkuId(),
                originalPayload.lotIds(),
                retryDates.preferredStartDate(),
                retryDates.preferredEndDate(),
                requestedAt.toLocalDate()
        );

        CreateStrategyCaseCommand command = new CreateStrategyCaseCommand(
                original.getCaseName(),
                original.getSkuId(),
                original.getRequestedSalesPointId(),
                originalPayload.lotIds(),
                originalPayload.candidateSalesPointIds(),
                originalPayload.strategyTypes(),
                retryDates.preferredStartDate(),
                retryDates.preferredEndDate()
        );

        try {
            StrategyCaseCreated created = strategyCaseService.createRetryStrategyCase(
                    command,
                    requesterId,
                    failedStrategyCaseId,
                    requestedAt
            );
            return RetryAiStrategyGenerationResponse.created(
                    failedStrategyCaseId,
                    created,
                    retryDates.toResponse(originalPayload)
            );
        } catch (AppException exception) {
            throw translateCreateFailure(exception);
        }
    }

    private StrategyCaseRequestPayload deserializePayload(String payloadJson) {
        try {
            return payloadSerializer.deserialize(payloadJson);
        } catch (StrategyCasePayloadException exception) {
            throw new AppException(ErrorCode.AI_STRATEGY_RETRY_PAYLOAD_INVALID);
        }
    }

    private static void validateStoredPayload(StrategyCaseRequestPayload payload) {
        if (payload == null
                || payload.lotIds() == null
                || payload.candidateSalesPointIds() == null
                || payload.strategyTypes() == null
                || payload.forecastStartDate() == null
                || payload.forecastEndDate() == null
                || (payload.preferredStartDate() != null
                && payload.preferredEndDate() != null
                && payload.preferredStartDate().isAfter(payload.preferredEndDate()))) {
            throw new AppException(ErrorCode.AI_STRATEGY_RETRY_PAYLOAD_INVALID);
        }
    }

    private static RetryDates resolveRetryDates(
            StrategyCaseRequestPayload payload,
            LocalDate today,
            StrategyRetryDateAdjustmentPolicy policy
    ) {
        LocalDate originalStart = payload.preferredStartDate();
        LocalDate originalEnd = payload.preferredEndDate();

        if (originalEnd != null && originalEnd.isBefore(today)) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_RETRY_PERIOD_EXPIRED,
                    ErrorCode.AI_STRATEGY_RETRY_PERIOD_EXPIRED.getMessage(),
                    new RetryPeriodExpiredDetails(originalStart, originalEnd, today)
            );
        }

        boolean startDatePassed = originalStart != null && originalStart.isBefore(today);
        if (startDatePassed && policy == StrategyRetryDateAdjustmentPolicy.REJECT) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_RETRY_DATE_ADJUSTMENT_REQUIRED,
                    ErrorCode.AI_STRATEGY_RETRY_DATE_ADJUSTMENT_REQUIRED.getMessage(),
                    new RetryDateAdjustmentRequiredDetails(
                            START_DATE_PASSED,
                            originalStart,
                            originalEnd,
                            today,
                            originalEnd
                    )
            );
        }

        return new RetryDates(
                startDatePassed ? today : originalStart,
                originalEnd,
                startDatePassed
        );
    }

    private void validateSelectedLotSellablePeriod(
            Long skuId,
            List<Long> lotIds,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            LocalDate today
    ) {
        if (lotIds.isEmpty()) {
            return;
        }
        List<StrategyLotReferenceVO> lots = strategyCaseMapper.selectLotReferences(lotIds);
        if (lots.size() != lotIds.size()
                || lots.stream().anyMatch(lot -> !Objects.equals(skuId, lot.getSkuId()))
                || lots.stream().anyMatch(lot -> !AVAILABLE_LOT_STATUS.equals(lot.getLotStatus()))) {
            throw new AppException(ErrorCode.AI_STRATEGY_RETRY_REFERENCE_CHANGED);
        }

        LocalDate maximumEndDate = latestSellableEndDate(lots);
        if (maximumEndDate == null) {
            return;
        }
        LocalDate effectiveStart = preferredStartDate == null ? today : preferredStartDate;
        // 예측 종료일은 수요 관측 범위일 뿐 전략 실행 종료일이 아니다. 사용자가 종료일을
        // 고정하지 않았다면 후보 생성 단계가 이 판매 가능 종료일 안에서 기간을 선택한다.
        if (maximumEndDate.isBefore(effectiveStart)
                || (preferredEndDate != null && preferredEndDate.isAfter(maximumEndDate))) {
            throw new AppException(
                    ErrorCode.AI_STRATEGY_RETRY_CONDITIONS_STALE,
                    "선택한 LOT의 소비기한 또는 판매중단일로 인해 기존 판매 기간을 사용할 수 없습니다.",
                    new RetryConditionsStaleDetails(
                            SELLABLE_PERIOD_EXCEEDED,
                            maximumEndDate,
                            preferredEndDate
                    )
            );
        }
    }

    /** 판매 가능일 제한이 없는 LOT가 하나라도 있으면 전체 SKU 기간을 여기서 제한하지 않는다. */
    private static LocalDate latestSellableEndDate(List<StrategyLotReferenceVO> lots) {
        LocalDate latest = null;
        for (StrategyLotReferenceVO lot : lots) {
            LocalDate sellableEnd = sellableEndDate(lot);
            if (sellableEnd == null) {
                return null;
            }
            if (latest == null || sellableEnd.isAfter(latest)) {
                latest = sellableEnd;
            }
        }
        return latest;
    }

    private static LocalDate sellableEndDate(StrategyLotReferenceVO lot) {
        LocalDate expiry = lot.getExpiryDate();
        LocalDate saleStop = lot.getSaleStopDate() == null
                ? null
                : lot.getSaleStopDate().minusDays(1);
        if (expiry == null) {
            return saleStop;
        }
        if (saleStop == null) {
            return expiry;
        }
        return expiry.isBefore(saleStop) ? expiry : saleStop;
    }

    private RetryAiStrategyGenerationResponse.DateAdjustment existingDateAdjustment(
            StrategyCaseVO original,
            StrategyCaseVO existingRetry
    ) {
        try {
            StrategyCaseRequestPayload originalPayload = payloadSerializer.deserialize(
                    original.getRequestPayloadJson()
            );
            StrategyCaseRequestPayload retryPayload = payloadSerializer.deserialize(
                    existingRetry.getRequestPayloadJson()
            );
            boolean applied = !Objects.equals(
                    originalPayload.preferredStartDate(),
                    retryPayload.preferredStartDate()
            ) || !Objects.equals(
                    originalPayload.preferredEndDate(),
                    retryPayload.preferredEndDate()
            );
            return new RetryAiStrategyGenerationResponse.DateAdjustment(
                    applied,
                    originalPayload.preferredStartDate(),
                    originalPayload.preferredEndDate(),
                    retryPayload.preferredStartDate(),
                    retryPayload.preferredEndDate()
            );
        } catch (StrategyCasePayloadException exception) {
            return null;
        }
    }

    private static AppException translateCreateFailure(AppException exception) {
        return switch (exception.getErrorCode()) {
            case AI_STRATEGY_SKU_NOT_FOUND,
                    AI_STRATEGY_LOT_NOT_FOUND,
                    AI_STRATEGY_LOT_NOT_BELONG_TO_SKU,
                    AI_STRATEGY_SALES_POINT_NOT_FOUND ->
                    new AppException(ErrorCode.AI_STRATEGY_RETRY_REFERENCE_CHANGED);
            case AI_STRATEGY_DATE_OUT_OF_RANGE,
                    AI_STRATEGY_START_AFTER_END,
                    AI_STRATEGY_UNSUPPORTED_TYPE ->
                    new AppException(ErrorCode.AI_STRATEGY_RETRY_CONDITIONS_STALE);
            case AI_STRATEGY_INVALID_REQUEST,
                    AI_STRATEGY_DUPLICATE_INPUT ->
                    new AppException(ErrorCode.AI_STRATEGY_RETRY_PAYLOAD_INVALID);
            default -> exception;
        };
    }

    private static void validateRequest(Long strategyCaseId, Long requesterId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (requesterId == null || requesterId <= 0) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
    }

    private record RetryDates(
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            boolean adjusted
    ) {
        private RetryAiStrategyGenerationResponse.DateAdjustment toResponse(
                StrategyCaseRequestPayload original
        ) {
            return new RetryAiStrategyGenerationResponse.DateAdjustment(
                    adjusted,
                    original.preferredStartDate(),
                    original.preferredEndDate(),
                    preferredStartDate,
                    preferredEndDate
            );
        }
    }
}
