package com.stockit.backend.feature.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationFailureServiceTest {

    @Mock
    private StrategyCaseMapper strategyCaseMapper;

    @Test
    void recordsFailureInRequiresNewTransactionAndLimitsMessageLength() throws Exception {
        StrategyGenerationFailureService service =
                new StrategyGenerationFailureService(strategyCaseMapper);
        when(strategyCaseMapper.markGenerationFailedIfGenerating(
                eq(12345L),
                eq("MQ_RETRY_EXHAUSTED"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);

        boolean updated = service.markFailed(
                12345L,
                "MQ_RETRY_EXHAUSTED",
                "x".repeat(2100)
        );

        assertThat(updated).isTrue();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(strategyCaseMapper).markGenerationFailedIfGenerating(
                eq(12345L),
                eq("MQ_RETRY_EXHAUSTED"),
                messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue()).hasSize(2000);

        Method method = StrategyGenerationFailureService.class.getMethod(
                "markFailed",
                Long.class,
                String.class,
                String.class
        );
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
