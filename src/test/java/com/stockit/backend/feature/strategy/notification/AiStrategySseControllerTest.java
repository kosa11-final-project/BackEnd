package com.stockit.backend.feature.strategy.notification;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.auth.vo.AuthUserVO;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiStrategySseControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiStrategySseEmitterRegistry emitterRegistry;

    @Test
    void opensSessionAuthenticatedEventStreamForCurrentUser() throws Exception {
        SseEmitter emitter = new SseEmitter(30_000L);
        emitter.send(SseEmitter.event().name("connected").data("{}"));
        when(emitterRegistry.subscribe(3L)).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/ai-strategies/events")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(header().string(
                        "Content-Type",
                        MediaType.TEXT_EVENT_STREAM_VALUE
                ))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(emitterRegistry).subscribe(3L);
        emitter.complete();
    }

    @Test
    void rejectsUnauthenticatedEventStream() throws Exception {
        mockMvc.perform(get("/api/v1/ai-strategies/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(emitterRegistry);
    }

    private static UsernamePasswordAuthenticationToken adminAuthentication() {
        AuthUserVO user = new AuthUserVO();
        user.setUserId(3L);
        user.setLoginId("requester");
        user.setPasswordHash("unused");
        user.setUserName("요청자");
        user.setEmail("requester@stockit.test");
        user.setOrganizationId(1L);
        user.setOrganizationName("StockIt");
        user.setRoleCode("GREENFOOD_ADMIN");
        AuthPrincipal principal = AuthPrincipal.from(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }
}
