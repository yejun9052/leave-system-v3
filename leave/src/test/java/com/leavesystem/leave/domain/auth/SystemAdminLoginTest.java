package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.domain.employee.Employee;
import com.leavesystem.leave.domain.employee.EmployeeRepository;
import com.leavesystem.leave.domain.employee.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevAuthController.class)
@Import(DevSecurityConfig.class)
@ActiveProfiles("dev")
class SystemAdminLoginTest {

    private static final String 로그인_아이디 = "admin";
    private static final String 비밀번호 = "admin1234!";

    @Autowired
    MockMvc mvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmployeeRepository employees;

    @Test
    void 관리자_로그인에_성공하면_세션으로_내_정보를_조회할_수_있다() throws Exception {
        관리자_계정_조회_준비();

        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", 로그인_아이디).param("password", 비밀번호))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/auth/me"))
                .andExpect(authenticated().withUsername(로그인_아이디).withRoles("SYS_ADMIN"))
                .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(로그인_아이디))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_SYS_ADMIN"));
    }

    @Test
    void 로그인_화면과_CSRF_토큰은_로그인_전에도_조회할_수_있다() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")));

        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").isNotEmpty())
                .andExpect(jsonPath("$.data.parameterName").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void CSRF_토큰이_없으면_로그인을_거부한다() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("username", 로그인_아이디).param("password", 비밀번호))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void 잘못된_비밀번호는_로그인을_거부한다() throws Exception {
        관리자_계정_조회_준비();

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", 로그인_아이디).param("password", "wrong-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 등록되지_않은_계정은_로그인을_거부한다() throws Exception {
        when(employees.findByLoginIdIgnoreCase("unknown")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "unknown").param("password", 비밀번호))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 비활성_관리자_계정은_로그인을_거부한다() throws Exception {
        Employee account = mock(Employee.class);
        when(account.getRole()).thenReturn(Role.SYS_ADMIN);
        when(account.getPasswordHash()).thenReturn(passwordEncoder.encode(비밀번호));
        when(account.getLoginId()).thenReturn(로그인_아이디);
        when(account.isActive()).thenReturn(false);
        when(employees.findByLoginIdIgnoreCase(로그인_아이디)).thenReturn(Optional.of(account));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", 로그인_아이디).param("password", 비밀번호))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 일반_사원_계정은_관리자_로그인을_할_수_없다() throws Exception {
        Employee member = Employee.builder()
                .name("사원")
                .email("member@company.com")
                .hireDate(LocalDate.of(2026, 1, 1))
                .role(Role.MEMBER)
                .active(true)
                .build();
        when(employees.findByLoginIdIgnoreCase(로그인_아이디)).thenReturn(Optional.of(member));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", 로그인_아이디).param("password", 비밀번호))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void 로그아웃하면_세션이_무효화된다() throws Exception {
        관리자_계정_조회_준비();
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", 로그인_아이디).param("password", 비밀번호))
                .andExpect(authenticated())
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 로그인하지_않으면_내_정보를_조회할_수_없다() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void 관리자_계정_조회_준비() {
        Employee account = Employee.systemAdmin("담당자", 로그인_아이디,
                passwordEncoder.encode(비밀번호));
        when(employees.findByLoginIdIgnoreCase(로그인_아이디)).thenReturn(Optional.of(account));
    }
}
