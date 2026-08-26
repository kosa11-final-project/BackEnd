package com.stockit.backend.feature.demandforecast.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "app.demand-forecast.alert.teams.enabled=true",
        "app.demand-forecast.alert.teams.webhook-url=https://example.test/teams-alert",
        "app.demand-forecast.alert.teams.scheduler-cooldown=10m"
})
@ActiveProfiles("test")
class DemandForecastTeamsAlertContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void loadsTeamsAlertBeansWhenEnabled() {
        assertThat(applicationContext.getBean(DemandForecastTeamsAlertListener.class))
                .isNotNull();
        assertThat(applicationContext.getBean(DemandForecastTeamsAlertSender.class))
                .isNotNull();
    }
}
