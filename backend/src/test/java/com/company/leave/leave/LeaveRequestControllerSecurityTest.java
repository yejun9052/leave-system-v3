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
    void systemAdminGets403ForEveryApprovalApiBeforeServiceIsCalled() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN));
        assertApprovalForbidden();
        verifyNoInteractions(service, balances);
    }

    @Test
    void mixedSuperAdminRolesCannotBypassApiRestrictions() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN, Role.HR_ADMIN, Role.TEAM_LEAD));
        assertApprovalForbidden();
        verifyNoInteractions(service, balances);
    }

    @Test
    void hrCanStillOpenInboxAndApproveRejectAndHandleCancellations() throws Exception {
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
    void teamLeadStillHasFirstStageApiButCannotDecideCancellations() throws Exception {
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
    void serviceDenialOfProxyCancellationIsReturnedAs403() throws Exception {
        authenticate(Set.of(Role.SUPER_ADMIN));
        when(service.cancel(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        mvc.perform(post("/api/leave-requests/10/cancel")).andExpect(status().isForbidden());
        verify(service).cancel(10L, 1L, null);
    }

    @Test
    void systemAdminCanStillReadAnotherEmployeesBalance() throws Exception {
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
