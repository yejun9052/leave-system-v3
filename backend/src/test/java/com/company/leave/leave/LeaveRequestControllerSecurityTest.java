package com.company.leave.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.exception.ErrorCode;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.common.exception.BusinessException;
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

/** 실제 메서드 인가 프록시와 HTTP 예외 처리를 검증한다. DB·Flyway는 기동하지 않는다. */
@DisplayName("휴가 결재 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestControllerSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private LeaveRequestService service;
    private LeaveBalanceService balances;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        service = mock(LeaveRequestService.class);
        balances = mock(LeaveBalanceService.class);
        context.register(MethodSecurity.class);
        context.registerBean(LeaveRequestService.class, () -> service);
        context.registerBean(LeaveBalanceService.class, () -> balances);
        context.registerBean(LeaveRequestController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(LeaveRequestController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 시스템_관리자는_모든_결재_API에서_서비스_호출_전에_403을_받는다() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN));
        assertApprovalForbidden();
        verifyNoInteractions(service, balances);
    }

    @Test
    void 시스템_관리자_권한에_다른_권한이_섞여도_결재_API를_우회할_수_없다() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN, Role.HR_ADMIN, Role.TEAM_LEAD));
        assertApprovalForbidden();
        verifyNoInteractions(service, balances);
    }

    @Test
    void 인사관리자는_결재함_승인_반려_취소_결재를_모두_할_수_있다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        when(service.pendingForApprover(1L)).thenReturn(List.of());
        mvc.perform(get("/api/leave-requests/pending")).andExpect(status().isOk());
        for (String action : List.of("approve", "reject", "cancel/approve", "cancel/reject")) {
            mvc.perform(post("/api/leave-requests/10/" + action).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"변경\"}")).andExpect(status().isOk());
        }
        verify(service).pendingForApprover(1L);
        verify(service).approve(10L, 1L);
        verify(service).reject(10L, 1L, "변경");
        verify(service).approveCancellation(10L, 1L);
        verify(service).rejectCancellation(10L, 1L, "변경");
    }

    @Test
    void 팀장은_1차_결재_API는_쓰지만_취소_결재는_403이다() throws Exception {
        authenticate(Set.of(Role.TEAM_LEAD));
        when(service.pendingForApprover(1L)).thenReturn(List.of());
        mvc.perform(get("/api/leave-requests/pending")).andExpect(status().isOk());
        mvc.perform(post("/api/leave-requests/10/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/leave-requests/10/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/leave-requests/10/cancel/approve")).andExpect(status().isForbidden());
        mvc.perform(post("/api/leave-requests/10/cancel/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void 서비스가_대리_취소를_거부하면_403으로_응답한다() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN));
        when(service.cancel(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        mvc.perform(post("/api/leave-requests/10/cancel")).andExpect(status().isForbidden());
        verify(service).cancel(10L, 1L, null);
    }

    @Test
    void 시스템_관리자는_다른_직원의_잔액을_조회할_수_있다() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN));
        mvc.perform(get("/api/leave-requests/balances/20").param("year", "2027"))
                .andExpect(status().isOk());
        verify(service).assertCanViewEmployeeData(1L, 20L);
        verify(balances).getResponse(20L, 2027);
    }

    private void assertApprovalForbidden() throws Exception {
        mvc.perform(get("/api/leave-requests/pending")).andExpect(status().isForbidden());
        for (String action : List.of("approve", "reject", "cancel/approve", "cancel/reject")) {
            mvc.perform(post("/api/leave-requests/10/" + action).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isForbidden());
        }
    }

    private void authenticate(Set<Role> roles) {
        Employee employee = Employee.builder().email("admin").name("관리자").roles(roles)
                .systemAccount(roles.contains(Role.SUPER_ADMIN)).build();
        ReflectionTestUtils.setField(employee, "id", 1L);
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
