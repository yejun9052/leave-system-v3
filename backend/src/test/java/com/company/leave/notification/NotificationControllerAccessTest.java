package com.company.leave.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
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

/** 알림 API: 누구나 쓰되 항상 로그인한 본인의 알림만 다룬다(다른 사람 id 를 받지 않음). DB는 기동하지 않는다. */
@DisplayName("알림 API")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private NotificationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(NotificationService.class);
        context.register(MethodSecurity.class);
        context.registerBean(NotificationService.class, () -> service);
        context.registerBean(NotificationController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(NotificationController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate(Set.of(Role.EMPLOYEE), 7L);
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 알림_목록은_본인_것을_기본_20건_조회하고_건수를_바꿀_수_있다() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").param("limit", "5")).andExpect(status().isOk());
        verify(service).list(7L, 20);
        verify(service).list(7L, 5);
    }

    @Test
    void 읽지_않은_알림_수를_count로_돌려준다() throws Exception {
        when(service.unreadCount(7L)).thenReturn(3L);
        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    void 모두_읽음은_본인_알림에만_적용한다() throws Exception {
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk());
        verify(service).markAllRead(7L);
    }

    private void authenticate(Set<Role> roles, Long id) {
        Employee employee = Employee.builder().email("user@company.com").name("사용자").roles(roles).build();
        ReflectionTestUtils.setField(employee, "id", id);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
