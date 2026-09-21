package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DevAuthController.class)
@Import(DevSecurityConfig.class)
@ActiveProfiles("dev")
class DevLoginTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EmployeeRepository employees;

    @ParameterizedTest
    @CsvSource({
            "leaveAdmin,leaveAdmin,leaveAdmin@company.com,HR_ADMIN",
            "leaveAdmin@company.com,leaveAdmin,leaveAdmin@company.com,HR_ADMIN",
            "admin,admin,admin@company.com,SYS_ADMIN",
            "manager,manager,manager@company.com,LEADER",
            "employee,employee,employee@company.com,MEMBER"
    })
    void logsInAndReusesSession(String username, String password, String email, Role role) throws Exception {
        mockEmployee(email, role, true);
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/me"))
                .andExpect(authenticated().withUsername(email).withRoles(role.name()))
                .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_" + role.name()));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFormIsAvailableAndCsrfIsRequired() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")));
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "admin"))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
    }

    @Test
    void rejectsWrongPasswordAndDisabledOrMissingEmployee() throws Exception {
        mockEmployee("admin@company.com", Role.SYS_ADMIN, true);
        assertLoginFails("admin", "wrong");
        mockEmployee("admin@company.com", Role.SYS_ADMIN, false);
        assertLoginFails("admin", "admin");
        when(employees.findByEmailIgnoreCase("admin@company.com")).thenReturn(Optional.empty());
        assertLoginFails("admin", "admin");
        assertLoginFails("unknown", "unknown");
    }

    private void assertLoginFails(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    private void mockEmployee(String email, Role role, boolean active) {
        when(employees.findByEmailIgnoreCase(email.toLowerCase(java.util.Locale.ROOT)))
                .thenReturn(Optional.of(Employee.builder().name("테스트").email(email)
                        .hireDate(LocalDate.of(2026, 1, 1)).role(role).active(active).build()));
    }
}
