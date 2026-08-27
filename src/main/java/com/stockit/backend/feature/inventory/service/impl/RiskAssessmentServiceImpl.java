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
        return toDetailResponse(evaluation.result(), evaluation.predictedQtyD30());
    }

    private RiskEvaluation evaluateInternal(String skuCode, String salesPointCode) {
        LocalDate today = LocalDate.now(clock);
        RiskForecastVO forecast = riskAssessmentMapper.selectLatestForecast(skuCode, salesPointCode, today);
        LocalDate forecastBaseDate = forecast == null ? null : forecast.getBaseDate();
        LocalDate observationDate = forecastBaseDate == null ? today : forecastBaseDate;

        InventoryQuantityVO quantities = riskAssessmentMapper.selectInventoryQuantities(skuCode, salesPointCode);
        BigDecimal safetyStockQty = riskAssessmentMapper.selectSafetyStock(
                skuCode,
                salesPointCode,
                today
        );
        BigDecimal predictedQtyD7 = forecast == null ? null : forecast.getPredictedQtyD7();
        BigDecimal predictedQtyD14 = forecast == null ? null : forecast.getPredictedQtyD14();
        BigDecimal predictedQtyD30 = forecast == null ? null : forecast.getPredictedQtyD30();
        List<LotRiskItem> lotRiskItems = riskAssessmentMapper.selectLotRiskItems(
                skuCode,
                salesPointCode
        );
        BigDecimal onHandQty = quantities == null ? null : quantities.getOnHandQty();
        RiskAssessmentInput input = new RiskAssessmentInput(
                skuCode,
                salesPointCode,
                onHandQty,
                predictedQtyD7,
                predictedQtyD14,
                predictedQtyD30,
                safetyStockQty,
                observationDate,
                lotRiskItems,
                predictedQtyD7 != null && predictedQtyD14 != null && predictedQtyD30 != null,
                forecastBaseDate != null && forecastBaseDate.isBefore(today.minusDays(14)),
                today
        );

        return new RiskEvaluation(riskRuleEngine.evaluate(input), predictedQtyD30);
    }

    private static String requiredCode(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, field + "는 1~100자여야 합니다.");
        }
        return value.trim();
    }

    private RiskAssessmentDetailResponse toDetailResponse(
            RiskAssessmentResult result,
            BigDecimal predictedQtyD30
    ) {
        List<RiskReasonDto> reasonDtos = result.reasons() == null
                ? List.of()
                : result.reasons().stream()
                        .map(r -> new RiskReasonDto(r.code(), r.message(), r.severity(), r.evidence()))
                        .toList();
        BigDecimal stockCoverageDays = calculateStockCoverageDays(result.availableQty(), predictedQtyD30);
        String shortageYn = deriveSafetyStockShortageYn(result);
        LocalDate referenceDate = result.assessedAt().atZone(BUSINESS_ZONE).toLocalDate();

        return new RiskAssessmentDetailResponse(
                result.assessmentStatus(),
                result.apiRiskGrade(),
                result.dbRiskGrade(),
                result.primaryReason(),
                result.ruleVersion(),
                result.assessedAt(),
                referenceDate,
                result.availableQty(),
                result.shortageQty30(),
                result.safetyGapQty(),
                result.projectedD7(),
                result.safetyStockQty(),
                result.expectedDisposalQty30(),
                result.expectedDisposalRate30(),
                result.nearestSaleEndDays(),
                result.nearestExpiryDays(),
                result.maxHoldingDays(),
                reasonDtos,
                stockCoverageDays,
                shortageYn
        );
    }

    private static BigDecimal calculateStockCoverageDays(BigDecimal availableQty, BigDecimal predictedQtyD30) {
        if (availableQty == null || predictedQtyD30 == null || predictedQtyD30.signum() <= 0) {
            return null;
        }

        return availableQty.multiply(BigDecimal.valueOf(30))
                .divide(predictedQtyD30, 1, RoundingMode.HALF_UP);
    }

    private static String deriveSafetyStockShortageYn(RiskAssessmentResult result) {
        return result.isCurrentStockUnderSafety() ? "Y" : "N";
    }

    private record RiskEvaluation(
            RiskAssessmentResult result,
            BigDecimal predictedQtyD30
    ) {
    }
}
