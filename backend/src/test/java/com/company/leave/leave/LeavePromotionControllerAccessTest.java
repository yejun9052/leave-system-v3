package com.company.leave.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 연차 촉진 API 인가: 대상 조회와 발송은 인사관리자·시스템 관리자만. 발송자는 로그인한 관리자. DB는 기동하지 않는다. */
@DisplayName("연차 촉진 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeavePromotionControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private LeavePromotionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(LeavePromotionService.class);
        context.register(MethodSecurity.class);
        context.registerBean(LeavePromotionService.class, () -> service);
        context.registerBean(LeavePromotionController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(LeavePromotionController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원과_팀장은_촉진_대상을_보거나_안내를_보낼_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(get("/api/leave/promotion/targets")).andExpect(status().isForbidden());
            mvc.perform(post("/api/leave/promotion/send").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeIds\":[3]}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void 대상_조회는_기본_6개월이고_개월과_검색어를_넘긴다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(get("/api/leave/promotion/targets")).andExpect(status().isOk());
        mvc.perform(get("/api/leave/promotion/targets").param("months", "2").param("keyword", "연구소"))
                .andExpect(status().isOk());
        verify(service).targets(6, null);
        verify(service).targets(2, "연구소");
    }

    @Test
    void 시스템_관리자가_보내면_고른_직원과_보낸_사람_id를_넘긴다() throws Exception {
        authenticate(Set.of(Role.SYSTEM_ADMIN));
        mvc.perform(post("/api/leave/promotion/send").contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeIds\":[3,4]}")).andExpect(status().isOk());
        verify(service).send(List.of(3L, 4L), 1L);
    }

    @Test
    void 아무도_고르지_않으면_400이고_보내지_않는다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(post("/api/leave/promotion/send").contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeIds\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/leave/promotion/send").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        verify(service, never()).send(any(), any());
        verify(service, never()).targets(anyInt(), any());
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
