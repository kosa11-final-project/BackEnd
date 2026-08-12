package com.stockit.backend.feature.tmp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TmpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsWelcomeMessageThroughMyBatis() throws Exception {
        mockMvc.perform(get("/api/v1/tmp/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Stockit backend is running."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void echoesValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tmp/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello stockit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("hello stockit"));
    }

    @Test
    void rejectsBlankMessageWithCommonErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/tmp/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("message"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("메시지는 필수입니다."));
    }
}
