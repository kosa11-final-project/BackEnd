package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.AiStrategyFailureAlertMapper;
import com.stockit.backend.feature.strategy.vo.AiStrategyFailureAlertVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyTeamsAlertListenerTest {
    @Mock
    private AiStrategyFailureAlertMapper mapper;
    @Mock
    private AiStrategyTeamsAlertMessageFactory messageFactory;
    @Mock
    private AiStrategyTeamsAlertSender sender;

    private AiStrategyTeamsAlertListener listener;

    @BeforeEach
    void setUp() {
        listener = new AiStrategyTeamsAlertListener(mapper, messageFactory, sender);
    }

    @Test
    void sendsOnlyCommittedFinalFailure() {
        StrategyGenerationStateChangedEvent event = failureEvent();
        AiStrategyFailureAlertVO alert = new AiStrategyFailureAlertVO();
        AiStrategyTeamsAlertMessage message = org.mockito.Mockito.mock(
                AiStrategyTeamsAlertMessage.class
        );
        when(mapper.selectFailureAlert(123L)).thenReturn(alert);
        when(messageFactory.create(alert)).thenReturn(message);

        listener.onGenerationStateChanged(event);

        verify(sender).send(message);
    }

    @Test
    void ignoresNonFailureStateChanges() {
        listener.onGenerationStateChanged(new StrategyGenerationStateChangedEvent(
                123L,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        ));

        verify(mapper, never()).selectFailureAlert(any());
        verify(sender, never()).send(any());
    }

    @Test
    void doesNotAffectOriginalFailureFlowWhenTeamsDeliveryFails() {
        AiStrategyFailureAlertVO alert = new AiStrategyFailureAlertVO();
        AiStrategyTeamsAlertMessage message = org.mockito.Mockito.mock(
                AiStrategyTeamsAlertMessage.class
        );
        when(mapper.selectFailureAlert(123L)).thenReturn(alert);
        when(messageFactory.create(alert)).thenReturn(message);
        org.mockito.Mockito.doThrow(new AiStrategyTeamsAlertDeliveryException(
                "TEAMS_ALERT_UNAVAILABLE",
                "unavailable"
        )).when(sender).send(message);

        assertThatCode(() -> listener.onGenerationStateChanged(failureEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsDeliveryWhenFinalFailureRowCannotBeRead() {
        when(mapper.selectFailureAlert(123L)).thenReturn(null);

        listener.onGenerationStateChanged(failureEvent());

        verify(messageFactory, never()).create(any());
        verify(sender, never()).send(any());
    }

    private static StrategyGenerationStateChangedEvent failureEvent() {
        return new StrategyGenerationStateChangedEvent(
                123L,
                StrategyCaseStatus.GENERATION_FAILED,
                StrategyGenerationStage.STRATEGY_GENERATING
        );
    }
}
