package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalAction;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PersistedApprovalHeader;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;

@ExtendWith(MockitoExtension.class)
class PersistedStrategyApprovalReaderTest {

    @Mock private StrategyApprovalMapper mapper;

    @Test
    void reconstructsCardAndRecipientsOnlyFromPersistedRows() {
        PersistedApprovalHeader header = header();
        PersistedApprovalAction action = action();
        ReviewRequestRecord request = request();
        when(mapper.selectPersistedApprovalHeader(123L)).thenReturn(header);
        when(mapper.selectPersistedApprovalActions(55L)).thenReturn(List.of(action));
        when(mapper.selectReviewRequestDeliveries(55L)).thenReturn(List.of(request));

        PreparedTeamsDelivery prepared =
                new PersistedStrategyApprovalReader(mapper).read(123L, 1L);

        assertThat(prepared.selectedOptionId()).isEqualTo("CAND-1");
        assertThat(prepared.cardData().targetQuantity())
                .isEqualByComparingTo("29");
        assertThat(prepared.cardData().discountRates())
                .containsExactly(new BigDecimal("0.15"));
        assertThat(prepared.cardData().startDate())
                .isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(prepared.recipients()).singleElement()
                .satisfies(recipient -> {
                    assertThat(recipient.email()).isEqualTo("reviewer@stockit.test");
                    assertThat(recipient.deliverable()).isTrue();
                });
    }

    @Test
    void rejectsReadyCaseWhenAnyReviewRequestIsNotSent() {
        PersistedApprovalHeader header = header();
        header.setCaseStatus(StrategyCaseStatus.READY_TO_EXECUTE);
        ReviewRequestRecord request = request();
        request.setReviewStatus(StrategyReviewStatus.PENDING);
        when(mapper.selectPersistedApprovalHeader(123L)).thenReturn(header);
        when(mapper.selectPersistedApprovalActions(55L))
                .thenReturn(List.of(action()));
        when(mapper.selectReviewRequestDeliveries(55L))
                .thenReturn(List.of(request));

        assertThatThrownBy(() ->
                new PersistedStrategyApprovalReader(mapper).read(123L, 1L))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DATABASE_ERROR);
    }

    private static PersistedApprovalHeader header() {
        PersistedApprovalHeader header = new PersistedApprovalHeader();
        header.setStrategyCaseId(123L);
        header.setRequesterOrganizationId(1L);
        header.setCaseStatus(StrategyCaseStatus.GENERATED);
        header.setFinalSelectionId(44L);
        header.setStrategyOptionId(55L);
        header.setConstraintText("candidateId=CAND-1\nselectionFingerprint=abc");
        header.setCaseName("테스트 Case");
        header.setSkuCode("SKU-1");
        header.setSkuName("테스트 상품");
        header.setRequesterName("요청자");
        header.setOptionName("15% 할인 전략");
        header.setRecommendationReason("재고 소진을 단축합니다.");
        header.setTargetQuantity(new BigDecimal("29"));
        header.setStrategyPrice(new BigDecimal("8500"));
        header.setExpectedSalesQty(new BigDecimal("27"));
        header.setExpectedRevenue(new BigDecimal("229500"));
        header.setTotalContributionMargin(new BigDecimal("54000"));
        header.setExpectedRemainingQty(new BigDecimal("2"));
        header.setPlannedStartDate(LocalDate.of(2026, 8, 25));
        header.setPlannedEndDate(LocalDate.of(2026, 8, 31));
        return header;
    }

    private static PersistedApprovalAction action() {
        PersistedApprovalAction action = new PersistedApprovalAction();
        action.setActionType(StrategyType.PRICE_DISCOUNT);
        action.setTargetSalesPointId(20L);
        action.setTargetSalesPointName("목표 판매처");
        action.setStrategyPrice(new BigDecimal("8500"));
        action.setDiscountRate(new BigDecimal("0.15"));
        action.setActionOrder(1);
        return action;
    }

    private static ReviewRequestRecord request() {
        ReviewRequestRecord request = new ReviewRequestRecord();
        request.setReviewRequestId(701L);
        request.setReviewerId(7L);
        request.setReviewStatus(StrategyReviewStatus.FAILED);
        request.setReviewerName("검토자");
        request.setReviewerEmail("reviewer@stockit.test");
        request.setReviewerActiveYn("Y");
        request.setReviewerIsDeleted(false);
        request.setRequesterName("요청자");
        return request;
    }
}
