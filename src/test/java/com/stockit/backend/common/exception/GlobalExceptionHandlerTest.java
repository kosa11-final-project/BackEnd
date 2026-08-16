package com.stockit.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import com.stockit.backend.common.api.ApiErrorResponse;

@WebMvcTest(TestApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStandardSuccessResponse() throws Exception {
        mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("stockit"))
                .andExpect(jsonPath("$.timestamp", endsWith("Z")))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-002"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이름은 필수입니다."));
    }

    @Test
    void returnsBadRequestErrorForUnreadableJson() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    void returnsAppExceptionUsingItsErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/test/app-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TMP-001"))
                .andExpect(jsonPath("$.message").value("테스트 요청이 충돌했습니다."));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON-006"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    void returnsNotFoundError() throws Exception {
        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-004"));
    }

    @Test
    void returnsMethodNotAllowedError() throws Exception {
        mockMvc.perform(get("/api/v1/test/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON-005"));
    }

    @Test
    void handlesBindExceptionWithGlobalAndFieldErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "이름은 필수입니다."));
        bindingResult.addError(new ObjectError("target", "글로벌 검증 오류입니다."));
        bindingResult.addError(new ObjectError("target", ""));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test/bind");

        ResponseEntity<ApiErrorResponse> response = handler.handleBindException(
                new BindException(bindingResult),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ApiErrorResponse body = java.util.Objects.requireNonNull(response.getBody());
        assertThat(body.code()).isEqualTo("COMMON-002");
        assertThat(body.fieldErrors()).hasSize(3);
        assertThat(body.fieldErrors().get(0).field()).isEqualTo("name");
        assertThat(body.fieldErrors().get(0).message()).isEqualTo("이름은 필수입니다.");
        assertThat(body.fieldErrors().get(1).field()).isEqualTo("_global");
        assertThat(body.fieldErrors().get(1).message()).isEqualTo("글로벌 검증 오류입니다.");
        assertThat(body.fieldErrors().get(2).field()).isEqualTo("_global");
        assertThat(body.fieldErrors().get(2).message()).isEqualTo(ErrorCode.INVALID_PARAMETER.getMessage());
    }
}
