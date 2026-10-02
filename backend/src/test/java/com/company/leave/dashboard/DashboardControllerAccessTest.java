package com.company.leave.dashboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 대시보드 API 인가: 개인 대시보드는 로그인한 본인 것, 전사 대시보드는 인사관리자·시스템 관리자만. DB는 기동하지 않는다. */
@DisplayName("대시보드 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DashboardControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private DashboardService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(DashboardService.class);
        context.register(MethodSecurity.class);
        context.registerBean(DashboardService.class, () -> service);
        context.registerBean(DashboardController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(DashboardController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 개인_대시보드는_로그인한_본인_id로_조회한다() throws Exception {
        authenticate(Set.of(Role.EMPLOYEE), 7L);
        mvc.perform(get("/api/dashboard/me")).andExpect(status().isOk());
        verify(service).personal(7L);
    }

    @Test
    void 일반_직원과_팀장은_전사_대시보드를_볼_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role), 7L);
            mvc.perform(get("/api/dashboard/admin")).andExpect(status().isForbidden());
        }
        verify(service, never()).admin();
    }

    @Test
    void 인사관리자와_시스템_관리자는_전사_대시보드를_본다() throws Exception {
        for (Role role : List.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN)) {
            authenticate(Set.of(role), 1L);
            mvc.perform(get("/api/dashboard/admin")).andExpect(status().isOk());
        }
        verify(service, times(2)).admin();
    }

    private void authenticate(Set<Role> roles, Long id) {
        Employee employee = Employee.builder().email("user@company.com").name("사용자").roles(roles)
                .systemAccount(roles.contains(Role.SYSTEM_ADMIN)).build();
        ReflectionTestUtils.setField(employee, "id", id);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
