package com.company.leave.department;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 부서 API 인가: 목록은 누구나, 만들기·고치기·옮기기·지우기는 인사관리자·시스템 관리자만. DB는 기동하지 않는다. */
@DisplayName("부서 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DepartmentControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private static final String BODY = "{\"name\":\"개발팀\",\"parentId\":1}";

    private AnnotationConfigApplicationContext context;
    private DepartmentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(DepartmentService.class);
        context.register(MethodSecurity.class);
        context.registerBean(DepartmentService.class, () -> service);
        context.registerBean(DepartmentController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(DepartmentController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원도_부서_목록을_트리나_평면으로_볼_수_있다() throws Exception {
        authenticate(Set.of(Role.EMPLOYEE));
        mvc.perform(get("/api/departments")).andExpect(status().isOk());
        mvc.perform(get("/api/departments").param("view", "flat")).andExpect(status().isOk());
        verify(service).getTree();
        verify(service).getFlat();
    }

    @Test
    void 일반_직원과_팀장은_부서를_만들거나_고치거나_옮기거나_지울_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/departments/5").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/departments/5/move").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newParentId\":2}")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/departments/5")).andExpect(status().isForbidden());
        }
        verify(service, never()).create(any());
        verify(service, never()).update(anyLong(), any());
        verify(service, never()).move(anyLong(), any());
        verify(service, never()).delete(anyLong());
    }

    @Test
    void 인사관리자와_시스템_관리자는_부서를_관리할_수_있다() throws Exception {
        for (Role role : List.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/departments/5").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            mvc.perform(patch("/api/departments/5/move").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newParentId\":2}")).andExpect(status().isOk());
            mvc.perform(delete("/api/departments/5")).andExpect(status().isOk());
        }
        verify(service, times(2)).create(any());
        verify(service, times(2)).update(eq(5L), any());
        verify(service, times(2)).move(eq(5L), any());
        verify(service, times(2)).delete(5L);
    }

    @Test
    void 부서_이름이_비어_있으면_400이고_만들지_않는다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/departments/5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
        verify(service, never()).update(anyLong(), any());
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
