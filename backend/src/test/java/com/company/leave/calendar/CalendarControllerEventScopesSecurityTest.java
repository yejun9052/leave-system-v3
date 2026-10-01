package com.company.leave.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.calendar.domain.CalendarEventScope;
import com.company.leave.calendar.dto.CalendarDtos;
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

/** 실제 메서드 인가 프록시로 일정 등록 범위 API 의 권한을 검증한다. DB 는 기동하지 않는다. */
@DisplayName("일정 등록 범위 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CalendarControllerEventScopesSecurityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private CalendarService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(CalendarService.class);
        context.register(MethodSecurity.class);
        context.registerBean(CalendarService.class, () -> service);
        context.registerBean(CalendarController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(CalendarController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원은_403이고_서비스를_호출하지_않는다() throws Exception {
        authenticate(Role.EMPLOYEE);

        mvc.perform(get("/api/calendar/event-scopes")).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void 팀장은_범위_목록을_받는다() throws Exception {
        authenticate(Role.TEAM_LEAD);
        when(service.eventScopes(any())).thenReturn(List.of(
                new CalendarDtos.EventScopeOption(CalendarEventScope.DEPARTMENT, 3L, "플랫폼파트 일정")));

        mvc.perform(get("/api/calendar/event-scopes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].scope").value("DEPARTMENT"))
                .andExpect(jsonPath("$.data[0].departmentId").value(3))
                .andExpect(jsonPath("$.data[0].label").value("플랫폼파트 일정"));
        verify(service).eventScopes(any());
    }

    @Test
    void 인사관리자와_시스템_관리자도_호출할_수_있다() throws Exception {
        when(service.eventScopes(any())).thenReturn(List.of());
        for (Role role : List.of(Role.HR_ADMIN, Role.SUPER_ADMIN)) {
            authenticate(role);
            mvc.perform(get("/api/calendar/event-scopes")).andExpect(status().isOk());
        }
    }

    private void authenticate(Role role) {
        Employee employee = Employee.builder().email("user").name("직원").roles(Set.of(role))
                .systemAccount(role == Role.SUPER_ADMIN).build();
        ReflectionTestUtils.setField(employee, "id", 1L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
