package com.company.leave.policy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.batch.AutomationController;
import com.company.leave.batch.AutomationService;
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

/**
 * 정책 API 인가: 연차 정책·포상·경조사·블랙아웃 조회는 누구나, 변경과 자동화(조회 포함)는 인사관리자·시스템 관리자만.
 * 블랙아웃 변경은 처리자 id 를 넘겨 공지 메일의 처리자로 쓴다. DB는 기동하지 않는다.
 */
@DisplayName("정책 API 인가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PolicyControllersAccessTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}

    private static final String POLICY = """
            {"grantBasis":"HIRE_DATE","fiscalStartMonth":1,"fiscalStartDay":1,
             "baseAnnualDays":15,"seniorityStepYears":2,"seniorityIncrementDays":1,"maxAnnualDays":25,
             "monthlyAccrualEnabled":true,"monthlyAccrualMax":11,"allowNegative":false,
             "halfDayEnabled":true,"hourlyEnabled":true,
             "maxConcurrentAbsence":0,"minAdvanceDays":0,"maxConsecutiveDays":0,
             "carryOverEnabled":false,"maxCarryOverDays":0,"nextPeriodReservationEnabled":true}
            """;
    private static final String AWARD = "{\"years\":10,\"bonusDays\":3,\"name\":\"10년 근속\"}";
    private static final String SPECIAL = "{\"name\":\"본인 결혼\",\"days\":5,\"leaveTypeCode\":\"FAMILY\"}";
    private static final String BLACKOUT = "{\"startDate\":\"2026-12-24\",\"endDate\":\"2026-12-31\",\"name\":\"결산\"}";
    private static final String AUTOMATION = "{\"promotionEnabled\":true,\"promotionMonths\":[6,2]}";

    private AnnotationConfigApplicationContext context;
    private PolicyService policy;
    private PolicyRulesService rules;
    private AutomationService automation;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        policy = mock(PolicyService.class);
        rules = mock(PolicyRulesService.class);
        automation = mock(AutomationService.class);
        context.register(MethodSecurity.class);
        context.registerBean(PolicyService.class, () -> policy);
        context.registerBean(PolicyRulesService.class, () -> rules);
        context.registerBean(AutomationService.class, () -> automation);
        context.registerBean(PolicyController.class);
        context.registerBean(PolicyRulesController.class);
        context.registerBean(AutomationController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(PolicyController.class),
                        context.getBean(PolicyRulesController.class), context.getBean(AutomationController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void 일반_직원도_연차_정책과_포상_경조사_블랙아웃_목록을_볼_수_있다() throws Exception {
        authenticate(Set.of(Role.EMPLOYEE));
        mvc.perform(get("/api/policy")).andExpect(status().isOk());
        mvc.perform(get("/api/policy/award-rules")).andExpect(status().isOk());
        mvc.perform(get("/api/policy/special-rules")).andExpect(status().isOk());
        mvc.perform(get("/api/policy/blackouts")).andExpect(status().isOk());
        verify(policy).get();
        verify(rules).listAwards();
        verify(rules).listSpecials();
        verify(rules).listBlackouts();
    }

    @Test
    void 일반_직원과_팀장은_정책과_규칙을_바꿀_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(put("/api/policy").contentType(MediaType.APPLICATION_JSON).content(POLICY))
                    .andExpect(status().isForbidden());
            for (String[] rule : List.of(new String[] {"award-rules", AWARD}, new String[] {"special-rules", SPECIAL},
                    new String[] {"blackouts", BLACKOUT})) {
                mvc.perform(post("/api/policy/" + rule[0]).contentType(MediaType.APPLICATION_JSON).content(rule[1]))
                        .andExpect(status().isForbidden());
                mvc.perform(put("/api/policy/" + rule[0] + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(rule[1])).andExpect(status().isForbidden());
                mvc.perform(delete("/api/policy/" + rule[0] + "/7")).andExpect(status().isForbidden());
            }
        }
        verify(policy, never()).update(any());
        verifyNoInteractions(rules);
    }

    @Test
    void 일반_직원과_팀장은_자동화를_보거나_바꿀_수_없다() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.TEAM_LEAD)) {
            authenticate(Set.of(role));
            mvc.perform(get("/api/policy/automation")).andExpect(status().isForbidden());
            mvc.perform(put("/api/policy/automation/promotion").contentType(MediaType.APPLICATION_JSON)
                    .content(AUTOMATION)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(automation);
    }

    @Test
    void 인사관리자는_정책과_포상_경조사를_바꿀_수_있다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(put("/api/policy").contentType(MediaType.APPLICATION_JSON).content(POLICY))
                .andExpect(status().isOk());
        mvc.perform(post("/api/policy/award-rules").contentType(MediaType.APPLICATION_JSON).content(AWARD))
                .andExpect(status().isOk());
        mvc.perform(put("/api/policy/award-rules/7").contentType(MediaType.APPLICATION_JSON).content(AWARD))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/policy/award-rules/7")).andExpect(status().isOk());
        mvc.perform(post("/api/policy/special-rules").contentType(MediaType.APPLICATION_JSON).content(SPECIAL))
                .andExpect(status().isOk());
        mvc.perform(put("/api/policy/special-rules/7").contentType(MediaType.APPLICATION_JSON).content(SPECIAL))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/policy/special-rules/7")).andExpect(status().isOk());
        verify(policy).update(any());
        verify(rules).createAward(any());
        verify(rules).updateAward(eq(7L), any());
        verify(rules).deleteAward(7L);
        verify(rules).createSpecial(any());
        verify(rules).updateSpecial(eq(7L), any());
        verify(rules).deleteSpecial(7L);
    }

    @Test
    void 블랙아웃을_바꾸면_처리한_관리자_id를_넘긴다() throws Exception {
        for (Role role : List.of(Role.HR_ADMIN, Role.SYSTEM_ADMIN)) {
            authenticate(Set.of(role));
            mvc.perform(post("/api/policy/blackouts").contentType(MediaType.APPLICATION_JSON).content(BLACKOUT))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/policy/blackouts/7").contentType(MediaType.APPLICATION_JSON).content(BLACKOUT))
                    .andExpect(status().isOk());
            mvc.perform(delete("/api/policy/blackouts/7")).andExpect(status().isOk());
        }
        verify(rules, times(2)).createBlackout(any(), eq(1L));
        verify(rules, times(2)).updateBlackout(eq(7L), any(), eq(1L));
        verify(rules, times(2)).deleteBlackout(7L, 1L);
    }

    @Test
    void 인사관리자는_자동화를_보고_촉진_자동_발송을_설정할_수_있다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(get("/api/policy/automation")).andExpect(status().isOk());
        mvc.perform(put("/api/policy/automation/promotion").contentType(MediaType.APPLICATION_JSON)
                .content(AUTOMATION)).andExpect(status().isOk());
        verify(automation).overview();
        verify(automation).updatePromotion(true, List.of(6, 2));
    }

    @Test
    void 필수값이_빠진_요청은_400이고_서비스를_부르지_않는다() throws Exception {
        authenticate(Set.of(Role.HR_ADMIN));
        mvc.perform(post("/api/policy/blackouts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-12-24\",\"endDate\":\"2026-12-31\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/policy/award-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"years\":0,\"bonusDays\":3}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/policy/special-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"본인 결혼\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/policy/automation/promotion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"promotionEnabled\":true}")).andExpect(status().isBadRequest());
        verifyNoInteractions(rules);
        verify(automation, never()).updatePromotion(anyBoolean(), any());
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
