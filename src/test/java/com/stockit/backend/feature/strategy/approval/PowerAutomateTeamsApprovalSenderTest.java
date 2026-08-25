package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

/** Teams 전송 실패와 내부 카드 생성 결함을 구분하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class PowerAutomateTeamsApprovalSenderTest {

    @Mock private RestClient restClient;
    @Mock private TeamsApprovalCardFactory cardFactory;
    @Mock private TeamsApprovalMessage message;

    private PowerAutomateTeamsApprovalSender sender;

    @BeforeEach
    void setUp() {
        TeamsApprovalProperties properties = new TeamsApprovalProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl("https://example.test/teams-webhook");
        sender = new PowerAutomateTeamsApprovalSender(
                restClient, properties, cardFactory
        );
    }

    @Test
    void doesNotMisclassifyCardCreationBugAsWebhookFailure() {
        NullPointerException programmingError = new NullPointerException("bug");
        when(cardFactory.create(message)).thenThrow(programmingError);

        assertThatThrownBy(() -> sender.send(message))
                .isSameAs(programmingError);
    }
}
