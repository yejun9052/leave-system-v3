package com.company.leave.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 휴가 종류 API 인가: 목록은 누구나, 추가·수정·삭제는 인사관리자·시스템 관리자만. DB는 기동하지 않는다. */
@DisplayName("휴가 종류 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveTypeControllerAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private static final String CREATE = """
            {"code":"REFRESH","name":"리프레시","deductDays":1,"paid":true,"annualDeductionMode":"NONE",
             "colorHex":"#22c55e"}
            """;
    private static final String UPDATE = """
            {"name":"리프레시","deductDays":1,"paid":true,"annualDeductionMode":"NONE",
             "colorHex":"#22c55e","active":true}
            """;

    private AnnotationConfigApplicationContext context;
    private LeaveTypeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(LeaveTypeService.class);
        context.register(MethodSecurity.class);
        context.registerBean(LeaveTypeService.class, () -> service);
        context.registerBean(LeaveTypeController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(LeaveTypeController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원도_휴가_종류_목록을_보고_비활성_포함_여부를_넘긴다() throws Exception {
        authenticate(Set.of(Role.EMPLOYEE));
        mvc.perform(get("/api/leave-types")).andExpect(status().isOk());
        mvc.perform(get("/api/leave-types").param("includeInactive", "true")).andExpect(status().isOk());
        verify(service).list(false);
        verify(service).list(true);
    }

    @Test
    void 일반_직원과_팀장은_휴가_종류를_바꿀_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/leave-types/3").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/leave-types/3")).andExpect(status().isForbidden());
        }
        verify(service, never()).create(any());
        verify(service, never()).update(anyLong(), any());
        verify(service, never()).delete(anyLong());
    }

    @Test
    void 인사관리자와_시스템_관리자는_휴가_종류를_추가_수정_삭제할_수_있다() throws Exception {
        for (Role role : List.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/leave-types/3").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                    .andExpect(status().isOk());
            mvc.perform(delete("/api/leave-types/3")).andExpect(status().isOk());
        }
        verify(service, times(2)).create(any());
        verify(service, times(2)).update(eq(3L), any());
        verify(service, times(2)).delete(3L);
    }

    @Test
    void 차감_방식이_빠지면_400이고_저장하지_않는다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE.replace("\"annualDeductionMode\":\"NONE\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/leave-types/3").contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE.replace("\"annualDeductionMode\":\"NONE\",", "")))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
        verify(service, never()).update(anyLong(), any());
    }

    @Test
    void 코드_이름_색이_빠지면_400이고_저장하지_않는다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"리프레시\",\"deductDays\":1,\"paid\":true,\"annualDeductionMode\":\"DEDUCT\","
                                + "\"sortOrder\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/leave-types/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deductDays\":1,\"paid\":true,\"annualDeductionMode\":\"DEDUCT\","
                                + "\"colorHex\":\"#000000\",\"active\":true}"))
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
