package com.company.leave.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.audit.AuditController;
import com.company.leave.audit.AuditService;
import com.company.leave.auth.SessionTerminator;
import com.company.leave.common.exception.GlobalExceptionHandler;
import com.company.leave.employee.EmployeeController;
import com.company.leave.employee.EmployeeExcelService;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.LeaveAdminController;
import com.company.leave.batch.AutomationController;
import com.company.leave.batch.AutomationService;
import com.company.leave.batch.JobRunRecorder;
import com.company.leave.leave.LeaveGrantService;
import com.company.leave.policy.PolicyController;
import com.company.leave.policy.PolicyService;
import com.company.leave.report.LeaveReportService;
import com.company.leave.report.ReportController;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 관리 전용 계정이 기존 관리 API의 실제 인가 프록시를 통과하는지 검증한다. */
@DisplayName("시스템 관리자 관리 API 접근")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SystemAdminManagementAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private EmployeeService employees;
    private PolicyService policy;
    private LeaveReportService reports;
    private AuditService audit;
    private LeaveGrantService grants;
    private AutomationService automation;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurity.class);
        employees = mock(EmployeeService.class);
        policy = mock(PolicyService.class);
        reports = mock(LeaveReportService.class);
        audit = mock(AuditService.class);
        grants = mock(LeaveGrantService.class);
        automation = mock(AutomationService.class);
        context.registerBean(EmployeeService.class, () -> employees);
        context.registerBean(EmployeeExcelService.class, () -> mock(EmployeeExcelService.class));
        context.registerBean(SessionTerminator.class, () -> mock(SessionTerminator.class));
        context.registerBean(PolicyService.class, () -> policy);
        context.registerBean(LeaveReportService.class, () -> reports);
        context.registerBean(AuditService.class, () -> audit);
        context.registerBean(LeaveGrantService.class, () -> grants);
        context.registerBean(JobRunRecorder.class, () -> new JobRunRecorder(mock(JdbcTemplate.class)));
        context.registerBean(AutomationService.class, () -> automation);
        context.registerBean(EmployeeController.class);
        context.registerBean(PolicyController.class);
        context.registerBean(ReportController.class);
        context.registerBean(AuditController.class);
        context.registerBean(LeaveAdminController.class);
        context.registerBean(AutomationController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(EmployeeController.class),
                context.getBean(PolicyController.class), context.getBean(ReportController.class),
                context.getBean(AuditController.class), context.getBean(LeaveAdminController.class),
                context.getBean(AutomationController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        Employee admin = Employee.builder().email("admin").name("시스템 관리자").systemAccount(true)
                .roles(Set.of(Role.SYSTEM_ADMIN)).build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        UserPrincipal principal = UserPrincipal.from(admin);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 시스템_관리자는_일반_직원을_조회_생성_수정할_수_있다() throws Exception {
        when(employees.search(any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get("/api/employees")).andExpect(status().isOk());
        String body = """
                {"email":"new@company.com","name":"직원","hireDate":"2024-01-01","roles":["HR_ADMIN"]}
                """;
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(put("/api/employees/20").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(employees).search(any(), any());
        verify(employees).create(any());
        verify(employees).update(eq(20L), any());
    }

    @Test
    void 시스템_관리자는_정책을_수정할_수_있다() throws Exception {
        mvc.perform(put("/api/policy").contentType(MediaType.APPLICATION_JSON).content("""
                {"grantBasis":"HIRE_DATE","fiscalStartMonth":1,"fiscalStartDay":1,
                 "baseAnnualDays":15,"seniorityStepYears":2,"seniorityIncrementDays":1,"maxAnnualDays":25,
                 "monthlyAccrualEnabled":true,"monthlyAccrualMax":11,"allowNegative":false,
                 "halfDayEnabled":true,"hourlyEnabled":true,"leadApprovalRequired":true,
                 "maxConcurrentAbsence":0,"minAdvanceDays":0,"maxConsecutiveDays":0,
                 "promotionEnabled":false,"carryOverEnabled":false,"maxCarryOverDays":0}
                """)).andExpect(status().isOk());
        verify(policy).update(any());
    }

    @Test
    void 시스템_관리자는_보고서를_내려받을_수_있다() throws Exception {
        when(reports.exportUsage(2027, List.of(), List.of())).thenReturn(new byte[] {1, 2, 3});
        mvc.perform(get("/api/reports/leave-usage/export").param("year", "2027"))
                .andExpect(status().isOk());
        verify(reports).exportUsage(2027, List.of(), List.of());
    }

    @Test
    void 보고서는_고른_부서와_사용자를_그대로_넘긴다() throws Exception {
        when(reports.exportUsage(2027, List.of(3L, 4L), List.of(9L))).thenReturn(new byte[] {1});
        mvc.perform(get("/api/reports/leave-usage/export").param("year", "2027")
                        .param("departmentIds", "3", "4").param("employeeIds", "9"))
                .andExpect(status().isOk());
        verify(reports).exportUsage(2027, List.of(3L, 4L), List.of(9L));
    }

    @Test
    void 시스템_관리자는_감사_로그를_볼_수_있다() throws Exception {
        when(audit.search(isNull(), any())).thenReturn(Page.empty(PageRequest.of(0, 30)));
        mvc.perform(get("/api/audit-logs")).andExpect(status().isOk());
        verify(audit).search(isNull(), any());
    }

    @Test
    void 시스템_관리자는_연차를_부여할_수_있다() throws Exception {
        when(grants.grantAll(2027)).thenReturn(3);
        mvc.perform(post("/api/leave/admin/grant").param("year", "2027"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/leave/admin/grant/20").param("year", "2027"))
                .andExpect(status().isOk());
        verify(grants).grantAll(2027);
        verify(grants).grantForEmployee(20L, 2027);
    }

    @Test
    void 시스템_관리자는_전_직원의_지금_연차_기간을_부여하고_실행_기록을_남긴다() throws Exception {
        when(grants.grantCurrentPeriods()).thenReturn(20);
        mvc.perform(post("/api/leave/admin/grant")).andExpect(status().isOk());
        verify(grants).grantCurrentPeriods();
    }

    @Test
    void 시스템_관리자는_자동화를_보고_촉진_자동_발송을_설정할_수_있다() throws Exception {
        mvc.perform(get("/api/policy/automation")).andExpect(status().isOk());
        mvc.perform(put("/api/policy/automation/promotion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionEnabled\":true,\"promotionMonths\":[6,2]}"))
                .andExpect(status().isOk());
        verify(automation).overview();
        verify(automation).updatePromotion(true, java.util.List.of(6, 2));
    }
}
