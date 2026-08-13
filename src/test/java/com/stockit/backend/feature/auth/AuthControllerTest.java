package com.stockit.backend.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.auth.security.AuthPrincipal;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/auth-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AuthControllerTest {

    private static final String LOGIN_ID = "greenfood-admin";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpAdministrator() {
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, organization_name, is_deleted) VALUES (1, ?, 0)",
                "그린푸드"
        );
        jdbcTemplate.update(
                "INSERT INTO app_role (role_id, role_code, is_deleted) VALUES (1, 'GREENFOOD_ADMIN', 0)"
        );
        jdbcTemplate.update(
                """
                INSERT INTO app_user (
                    user_id, organization_id, login_id, password_hash, user_name, email,
                    active_yn, updated_by, is_deleted
                ) VALUES (1, 1, ?, ?, ?, ?, 'Y', 1, 0)
                """,
                LOGIN_ID,
                passwordEncoder.encode(PASSWORD),
                "전체 총괄",
                "admin@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO user_role (user_role_id, user_id, role_id, is_deleted) VALUES (1, 1, 1, 0)"
        );
    }

    @Test
    void issuesCsrfTokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void logsInAndRestoresCurrentUserFromSession() throws Exception {
        MockHttpSession session = login();

        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        AuthPrincipal principal = (AuthPrincipal) securityContext.getAuthentication().getPrincipal();
        assertThat(principal.getPassword()).isNull();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.loginId").value(LOGIN_ID))
                .andExpect(jsonPath("$.data.userName").value("전체 총괄"))
                .andExpect(jsonPath("$.data.organizationName").value("그린푸드"))
                .andExpect(jsonPath("$.data.roleCode").value("GREENFOOD_ADMIN"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_login_at FROM app_user WHERE user_id = 1",
                Object.class
        )).isNotNull();
    }

    @Test
    void rejectsLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-003"));
    }

    @Test
    void hidesWhetherLoginIdOrPasswordWasWrong() throws Exception {
        mockMvc.perform(loginRequest(loginBody("wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        mockMvc.perform(loginRequest(
                        "{\"loginId\":\"missing\",\"password\":\"wrong-password\"}"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    void rejectsInactiveDeletedOrUnassignedUser() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET active_yn = 'N' WHERE user_id = 1");
        expectAuthenticationFailure();

        jdbcTemplate.update("UPDATE app_user SET active_yn = 'Y', is_deleted = 1 WHERE user_id = 1");
        expectAuthenticationFailure();

        jdbcTemplate.update("UPDATE app_user SET is_deleted = 0 WHERE user_id = 1");
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = 1");
        expectAuthenticationFailure();
    }

    @Test
    void expiresPreviousSessionWhenSameAccountLogsInAgain() throws Exception {
        MockHttpSession firstSession = login();
        MockHttpSession secondSession = login();

        mockMvc.perform(get("/api/v1/auth/me").session(firstSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        mockMvc.perform(get("/api/v1/auth/me").session(secondSession))
                .andExpect(status().isOk());
    }

    @Test
    void invalidatesSessionOnLogout() throws Exception {
        MockHttpSession session = login();
        CsrfCredentials csrf = requestCsrf(session);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthenticatedApiRequestWithJsonResponse() throws Exception {
        mockMvc.perform(get("/api/v1/tmp/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(loginBody(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.data.loginId").value(LOGIN_ID))
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void expectAuthenticationFailure() throws Exception {
        mockMvc.perform(loginRequest(loginBody(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    private MockHttpServletRequestBuilder loginRequest(String body) throws Exception {
        CsrfCredentials csrf = requestCsrf(null);
        return post("/api/v1/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private CsrfCredentials requestCsrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).path("data").path("token").asText();
        String headerName = objectMapper.readTree(responseBody).path("data").path("headerName").asText();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return new CsrfCredentials(cookie, token, headerName);
    }

    private static String loginBody(String password) {
        return "{\"loginId\":\"" + LOGIN_ID + "\",\"password\":\"" + password + "\"}";
    }

    private record CsrfCredentials(Cookie cookie, String token, String headerName) {
    }
}
