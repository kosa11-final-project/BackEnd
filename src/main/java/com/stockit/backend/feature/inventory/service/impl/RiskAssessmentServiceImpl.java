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
        return toDetailResponse(evaluation);
    }

    private RiskEvaluation evaluateInternal(String skuCode, String salesPointCode) {
        LocalDate today = LocalDate.now(clock);
        RiskForecastVO forecast = riskAssessmentMapper.selectLatestForecast(skuCode, salesPointCode, today);
        LocalDate forecastBaseDate = forecast == null ? null : forecast.getBaseDate();

        InventoryQuantityVO quantities = riskAssessmentMapper.selectInventoryQuantities(skuCode, salesPointCode);
        BigDecimal safetyStockQty = riskAssessmentMapper.selectSafetyStock(
                skuCode,
                salesPointCode,
                today
        );
        BigDecimal predictedQtyD7 = forecast == null ? null : forecast.getPredictedQtyD7();
        BigDecimal predictedQtyD14 = forecast == null ? null : forecast.getPredictedQtyD14();
        BigDecimal predictedQtyD30 = forecast == null ? null : forecast.getPredictedQtyD30();
        BigDecimal predictedQtyD60 = forecast == null ? null : forecast.getPredictedQtyD60();
        BigDecimal predictedQtyD90 = forecast == null ? null : forecast.getPredictedQtyD90();
        List<LotRiskItem> lotRiskItems = riskAssessmentMapper.selectLotRiskItems(
                skuCode,
                salesPointCode
        );
        PersistedRiskAssessmentVO persistedAssessment = riskAssessmentMapper.selectLatestPersistedAssessment(
                skuCode,
                salesPointCode
        );
        BigDecimal onHandQty = quantities == null ? null : quantities.getOnHandQty();
        BigDecimal reservedQty = lotRiskItems == null ? BigDecimal.ZERO : lotRiskItems.stream()
                .map(LotRiskItem::reservedQty)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RiskAssessmentInput input = new RiskAssessmentInput(
                skuCode,
                salesPointCode,
                onHandQty,
                reservedQty,
                predictedQtyD7,
                predictedQtyD14,
                predictedQtyD30,
                predictedQtyD60,
                predictedQtyD90,
                safetyStockQty,
                today,
                lotRiskItems,
                forecast != null,
                forecastBaseDate != null && forecastBaseDate.isBefore(today.minusDays(1)),
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

    private RiskAssessmentDetailResponse toDetailResponse(RiskEvaluation evaluation) {
        RiskAssessmentResult result = evaluation.result();
        BigDecimal predictedQtyD30 = evaluation.predictedQtyD30();
        PersistedRiskAssessmentVO persisted = evaluation.persistedAssessment();

        // 화면의 목록·상세 상태가 갈라지지 않도록 동기화 때 저장한 판정을 등급·사유의 기준으로 사용합니다.
        // 현재 수량과 예상 폐기 지표는 별도 컬럼으로 저장하지 않고 조회 시 계산한 결과를 계속 제공합니다.
        // 필수 horizon이 누락되거나 단조성이 깨진 예측은 엔진이 INVALID로
        // 분류하므로, 그 경우 조회용 현재 지표에도 유효하지 않은 수요를
        // 섞지 않습니다.
        BigDecimal stockCoverageDays = RiskRuleEngine.FORECAST_VALID.equals(result.forecastUsability())
                ? calculateStockCoverageDays(result.availableQty(), predictedQtyD30)
                : null;
        String shortageYn = deriveSafetyStockShortageYn(result);
        String assessmentStatus = persisted == null ? "UNASSESSED" : "ASSESSED";
        String dbRiskGrade = persisted == null ? null : persisted.getDbRiskGrade();
        String apiRiskGrade = dbRiskGrade;
        String reasonMessage = persisted == null ? null : persisted.getReasonMessage();
        String ruleVersion = persisted == null ? null : persisted.getRuleVersion();
        var assessedAt = persisted == null || persisted.getAssessedAt() == null
                ? null
                : persisted.getAssessedAt().toInstant();
        LocalDate referenceDate = assessedAt == null
                ? result.assessedAt().atZone(BUSINESS_ZONE).toLocalDate()
                : assessedAt.atZone(BUSINESS_ZONE).toLocalDate();
        // 호환용 reasons 배열도 저장된 canonical 사유 한 건만 전달합니다. 조회 시 계산된 INFO
        // 안내를 섞으면 동기화 스냅샷과 화면 문장이 달라질 수 있습니다.
        List<RiskReasonDto> reasonDtos = persisted == null || reasonMessage == null || reasonMessage.isBlank()
                ? List.of()
                : List.of(new RiskReasonDto(
                        "CANONICAL_REASON",
                        reasonMessage,
                        persisted.getDbRiskGrade(),
                        null
                ));
        Integer nearestExpiryDays = result.nearestExpiryDays();
        Integer maxHoldingDays = result.maxHoldingDays();

        return new RiskAssessmentDetailResponse(
                assessmentStatus,
                apiRiskGrade,
                dbRiskGrade,
                reasonMessage,
                ruleVersion,
                assessedAt,
                referenceDate,
                result.availableQty(),
                result.shortageQty30(),
                result.safetyGapQty(),
                result.projectedD7(),
                result.safetyStockQty(),
                result.expectedDisposalQty30(),
                result.expectedDisposalRate30(),
                result.nearestSaleEndDays(),
                nearestExpiryDays,
                maxHoldingDays,
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
            BigDecimal predictedQtyD30,
            PersistedRiskAssessmentVO persistedAssessment
    ) {
    }
}
