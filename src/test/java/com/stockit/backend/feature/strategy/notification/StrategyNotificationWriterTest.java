package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

@ExtendWith(MockitoExtension.class)
class StrategyNotificationWriterTest {

    @Mock private StrategyNotificationMapper mapper;

    @Test
    void writesUnreadCompletedNotificationWithStableDeduplicationKey()
            throws Exception {
        when(mapper.insertIfAbsent(
                7L,
                101L,
                StrategyNotificationWriter.COMPLETED_TYPE,
                "INFO",
                "AI 전략 생성 완료",
                "'테스트 AI 전략' 생성이 완료되었습니다.",
                "AI_STRATEGY:101:GENERATED"
        )).thenReturn(1);

        assertThat(writer().writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATED
        )).isTrue();

        Method method = StrategyNotificationWriter.class.getMethod(
                "writeFinalNotification",
                Long.class,
                Long.class,
                String.class,
                StrategyCaseStatus.class
        );
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void writesFailedNotificationWithoutInternalFailureDetails() {
        when(mapper.insertIfAbsent(
                7L,
                101L,
                StrategyNotificationWriter.FAILED_TYPE,
                "ERROR",
                "AI 전략 생성 실패",
                "'테스트 AI 전략' 생성에 실패했습니다.",
                "AI_STRATEGY:101:GENERATION_FAILED"
        )).thenReturn(1);

        assertThat(writer().writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATION_FAILED
        )).isTrue();
    }

    @Test
    void reportsExistingDeduplicatedNotificationWithoutDuplicatingIt() {
        when(mapper.insertIfAbsent(
                7L,
                101L,
                StrategyNotificationWriter.COMPLETED_TYPE,
                "INFO",
                "AI 전략 생성 완료",
                "'AI 전략' 생성이 완료되었습니다.",
                "AI_STRATEGY:101:GENERATED"
        )).thenReturn(0);

        assertThat(writer().writeFinalNotification(
                7L,
                101L,
                " ",
                StrategyCaseStatus.GENERATED
        )).isFalse();
    }

    @Test
    void treatsConcurrentDeduplicationConstraintConflictAsAlreadyWritten() {
        when(mapper.insertIfAbsent(
                7L,
                101L,
                StrategyNotificationWriter.COMPLETED_TYPE,
                "INFO",
                "AI 전략 생성 완료",
                "'테스트 AI 전략' 생성이 완료되었습니다.",
                "AI_STRATEGY:101:GENERATED"
        )).thenThrow(new DuplicateKeyException(
                "ORA-00001: unique constraint (UQ_NOTIFICATION_DEDUPE) violated"
        ));

        assertThat(writer().writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATED
        )).isFalse();
    }

    @Test
    void propagatesUnrelatedDuplicateKeyViolation() {
        DuplicateKeyException exception = new DuplicateKeyException(
                "ORA-00001: unique constraint (PK_NOTIFICATION) violated"
        );
        when(mapper.insertIfAbsent(
                7L,
                101L,
                StrategyNotificationWriter.COMPLETED_TYPE,
                "INFO",
                "AI 전략 생성 완료",
                "'테스트 AI 전략' 생성이 완료되었습니다.",
                "AI_STRATEGY:101:GENERATED"
        )).thenThrow(exception);

        assertThatThrownBy(() -> writer().writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATED
        )).isSameAs(exception);
    }

    @Test
    void rejectsIntermediateStatus() {
        assertThatThrownBy(() -> writer().writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATING
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private StrategyNotificationWriter writer() {
        return new StrategyNotificationWriter(mapper);
    }
}
