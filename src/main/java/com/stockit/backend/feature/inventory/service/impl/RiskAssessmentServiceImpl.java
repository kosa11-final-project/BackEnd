package com.stockit.backend.feature.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventory.dto.response.RiskAssessmentDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.RiskReasonDto;
import com.stockit.backend.feature.inventory.mapper.RiskAssessmentMapper;
import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput;
import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput.LotRiskItem;
import com.stockit.backend.feature.inventory.risk.RiskAssessmentResult;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;
import com.stockit.backend.feature.inventory.risk.InventoryQuantityVO;
import com.stockit.backend.feature.inventory.risk.PersistedRiskAssessmentVO;
import com.stockit.backend.feature.inventory.risk.RiskForecastVO;
import com.stockit.backend.feature.inventory.service.RiskAssessmentService;

@Service
@Transactional(readOnly = true)
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final RiskAssessmentMapper riskAssessmentMapper;
    private final RiskRuleEngine riskRuleEngine;
    private final Clock clock;

    @Autowired
    public RiskAssessmentServiceImpl(
            RiskAssessmentMapper riskAssessmentMapper,
            RiskRuleEngine riskRuleEngine
    ) {
        this(riskAssessmentMapper, riskRuleEngine, Clock.system(BUSINESS_ZONE));
    }

    RiskAssessmentServiceImpl(
            RiskAssessmentMapper riskAssessmentMapper,
            RiskRuleEngine riskRuleEngine,
            Clock clock
    ) {
        this.riskAssessmentMapper = riskAssessmentMapper;
        this.riskRuleEngine = riskRuleEngine;
        this.clock = clock;
    }

    @Override
    public RiskAssessmentDetailResponse getRiskAssessment(String skuCode, String salesPointCode) {
        String normalizedSkuCode = requiredCode(skuCode, "skuCode");
        String normalizedSalesPointCode = requiredCode(salesPointCode, "salesPointCode");

        RiskEvaluation evaluation = evaluateInternal(normalizedSkuCode, normalizedSalesPointCode);
        return toDetailResponse(evaluation.result(), evaluation.predictedQtyD30(), evaluation.persistedAssessment());
    }

    private RiskEvaluation evaluateInternal(String skuCode, String salesPointCode) {
        LocalDate today = LocalDate.now(clock);
        RiskForecastVO forecast = riskAssessmentMapper.selectLatestForecast(skuCode, salesPointCode);
        LocalDate forecastBaseDate = forecast == null ? null : forecast.getBaseDate();
        LocalDate observationDate = forecastBaseDate == null ? today : forecastBaseDate;

        InventoryQuantityVO quantities = riskAssessmentMapper.selectInventoryQuantities(skuCode, salesPointCode);
        BigDecimal safetyStockQty = riskAssessmentMapper.selectSafetyStock(
                skuCode,
                salesPointCode,
                today
        );
        BigDecimal predictedQtyD7 = forecast == null ? null : forecast.getPredictedQtyD7();
        BigDecimal predictedQtyD30 = forecast == null ? null : forecast.getPredictedQtyD30();
        List<LotRiskItem> lotRiskItems = riskAssessmentMapper.selectLotRiskItems(
                skuCode,
                salesPointCode,
                today
        );
        PersistedRiskAssessmentVO persistedAssessment = riskAssessmentMapper.selectLatestPersistedAssessment(
                skuCode,
                salesPointCode
        );

        BigDecimal onHandQty = quantities == null ? null : quantities.getOnHandQty();
        RiskAssessmentInput input = new RiskAssessmentInput(
                skuCode,
                salesPointCode,
                onHandQty,
                predictedQtyD7,
                predictedQtyD30,
                safetyStockQty,
                observationDate,
                lotRiskItems,
                predictedQtyD7 != null && predictedQtyD30 != null,
                forecastBaseDate != null && forecastBaseDate.isBefore(today.minusDays(14)),
                today
        );

        return new RiskEvaluation(riskRuleEngine.evaluate(input), predictedQtyD30, persistedAssessment);
    }

    private static String requiredCode(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, field + "는 1~100자여야 합니다.");
        }
        return value.trim();
    }

    private RiskAssessmentDetailResponse toDetailResponse(
            RiskAssessmentResult result,
            BigDecimal predictedQtyD30,
            PersistedRiskAssessmentVO persisted
    ) {
        // 동기화 결과가 있으면 판정 사유는 재평가하지 않고 RISK_ASSESSMENT.reason_message를 그대로 사용합니다.
        // 세부 reasons는 같은 내용을 다시 보여주게 되므로, 저장 결과가 있는 경우에는 비워 중복을 막습니다.
        List<RiskReasonDto> reasonDtos;
        if (persisted != null) {
            reasonDtos = List.of();
        } else if (result.reasons() != null) {
            reasonDtos = result.reasons().stream()
                    .map(r -> new RiskReasonDto(r.code(), r.message(), r.severity(), r.evidence()))
                    .toList();
        } else {
            reasonDtos = List.of();
        }

        BigDecimal stockCoverageDays = persisted != null
                ? persisted.getStockDays()
                : calculateStockCoverageDays(result.availableQty(), predictedQtyD30);
        String shortageYn;
        // 저장 결과가 있으면 RISK_ASSESSMENT.shortage_yn을 그대로 노출합니다.
        // 이 컬럼은 D+30 예측 부족량이 아니라 안전재고 미달 여부입니다.
        if (persisted != null && persisted.getShortageYn() != null) {
            shortageYn = persisted.getShortageYn();
        } else {
            shortageYn = deriveSafetyStockShortageYn(result);
        }
        String dbRiskGrade = persisted != null && persisted.getDbRiskGrade() != null
                ? persisted.getDbRiskGrade()
                : result.dbRiskGrade();
        String apiRiskGrade = persisted != null
                ? apiRiskGrade(dbRiskGrade, result.apiRiskGrade())
                : result.apiRiskGrade();
        String reasonMessage = persisted != null && persisted.getReasonMessage() != null
                ? persisted.getReasonMessage()
                : result.primaryReason();
        String ruleVersion = persisted != null && persisted.getRuleVersion() != null
                ? persisted.getRuleVersion()
                : result.ruleVersion();
        var assessedAt = persisted != null && persisted.getAssessedAt() != null
                ? persisted.getAssessedAt().toInstant()
                : result.assessedAt();
        Integer nearestExpiryDays = persisted != null
                ? persisted.getExpiryDaysLeft()
                : result.nearestExpiryDays();
        Integer maxHoldingDays = persisted != null
                ? persisted.getHoldingDays()
                : result.maxHoldingDays();

        return new RiskAssessmentDetailResponse(
                result.assessmentStatus(),
                apiRiskGrade,
                dbRiskGrade,
                reasonMessage,
                ruleVersion,
                assessedAt,
                result.baseDate(),
                result.availableQty(),
                result.shortageQty30(),
                result.safetyGapQty(),
                result.projectedD7(),
                result.safetyStockQty(),
                nearestExpiryDays,
                maxHoldingDays,
                reasonDtos,
                stockCoverageDays,
                shortageYn
        );
    }

    private static String apiRiskGrade(String dbRiskGrade, String fallback) {
        if (dbRiskGrade == null) {
            return fallback;
        }
        return switch (dbRiskGrade.toUpperCase()) {
            case "CRITICAL" -> "DANGER";
            case "WARNING" -> "CAUTION";
            case "NORMAL" -> "NORMAL";
            case "GOOD" -> "SAFE";
            default -> fallback;
        };
    }

    private static BigDecimal calculateStockCoverageDays(BigDecimal availableQty, BigDecimal predictedQtyD30) {
        if (availableQty == null || predictedQtyD30 == null || predictedQtyD30.signum() <= 0) {
            return null;
        }

        return availableQty.multiply(BigDecimal.valueOf(30))
                .divide(predictedQtyD30, 1, RoundingMode.HALF_UP);
    }

    private static String deriveSafetyStockShortageYn(RiskAssessmentResult result) {
        BigDecimal availableQty = result.availableQty();
        BigDecimal safetyStockQty = result.safetyStockQty();
        if (availableQty == null || availableQty.signum() == 0) {
            return "Y";
        }
        if (safetyStockQty == null) {
            return null;
        }
        return availableQty.compareTo(safetyStockQty) < 0 ? "Y" : "N";
    }

    private record RiskEvaluation(
            RiskAssessmentResult result,
            BigDecimal predictedQtyD30,
            PersistedRiskAssessmentVO persistedAssessment
    ) {
    }
}
