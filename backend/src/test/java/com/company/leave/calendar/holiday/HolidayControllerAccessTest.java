package com.company.leave.calendar.holiday;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.time.LocalDate;
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

/** 공휴일 API 인가: 목록은 누구나(대시보드 다가오는 휴일), 수동 동기화는 인사관리자·시스템 관리자만. DB는 기동하지 않는다. */
@DisplayName("공휴일 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidayControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private HolidayRepository repository;
    private HolidaySyncService syncService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        repository = mock(HolidayRepository.class);
        syncService = mock(HolidaySyncService.class);
        context.register(MethodSecurity.class);
        context.registerBean(HolidayRepository.class, () -> repository);
        context.registerBean(HolidaySyncService.class, () -> syncService);
        context.registerBean(HolidayController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(HolidayController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 사원과_팀장도_공휴일_목록을_볼_수_있다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(get("/api/holidays").param("year", "2026")).andExpect(status().isOk());
        }
        verify(repository, times(2)).findByDateBetweenOrderByDateAsc(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
    }

    @Test
    void 사원과_팀장은_공휴일을_동기화할_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/holidays/sync").param("year", "2026")).andExpect(status().isForbidden());
        }
        verify(syncService, never()).sync(anyInt());
    }

    @Test
    void 인사관리자와_시스템_관리자는_공휴일을_동기화할_수_있다() throws Exception {
        for (Role role : List.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/holidays/sync").param("year", "2026")).andExpect(status().isOk());
        }
        verify(syncService, times(2)).sync(2026);
    }

    private void authenticate(Set<Role> roles) {
        Employee employee = Employee.builder().email("user@company.com").name("사용자").roles(roles)
                .systemAccount(roles.contains(Role.SYSTEM_ADMIN)).build();
        ReflectionTestUtils.setField(employee, "id", 1L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
