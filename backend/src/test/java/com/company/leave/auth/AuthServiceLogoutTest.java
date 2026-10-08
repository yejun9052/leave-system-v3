package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuthService#logout, #me 단위 테스트. (로그인은 AuthServiceLoginTest)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 로그아웃·내 정보")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthServiceLogoutTest {

    private static final long EMPLOYEE_ID = 42L;

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private SecurityContextRepository securityContextRepository;

    private AuthService authService;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, employeeRepository, auditService,
                loginAttemptService, securityContextRepository, CookieCsrfTokenRepository.withHttpOnlyFalse());
        request = new MockHttpServletRequest("POST", "/api/auth/logout");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그아웃하면_현재_세션을_폐기하고_인증_정보를_비운다() {
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        로그인된_상태(직원(false));

        authService.logout(request, response);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 로그아웃하면_CSRF_토큰_쿠키를_삭제한다() {
        request.setCookies(new Cookie("XSRF-TOKEN", "token"));
        로그인된_상태(직원(false));

        authService.logout(request, response);

        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getMaxAge()).isZero();
    }

    @Test
    void 로그인_사용자의_로그아웃은_감사_로그를_남긴다() {
        로그인된_상태(직원(false));

        authService.logout(request, response);

        verify(auditService).record(eq(EMPLOYEE_ID), eq("테스트사원"), eq("LOGOUT"), eq("auth"), isNull(),
                anyString(), eq(true));
    }

    @Test
    void 로그인하지_않은_상태로_로그아웃해도_오류_없이_끝나고_감사_로그는_남기지_않는다() {
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        authService.logout(request, response);

        assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(auditService);
    }

    @Test
    void 내_정보는_직원_ID로_조회하고_입사일과_비밀번호_변경_필요_여부를_포함한다() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(직원(true)));

        MeResponse me = authService.me(EMPLOYEE_ID);

        assertThat(me.id()).isEqualTo(EMPLOYEE_ID);
        assertThat(me.phone()).isEqualTo("010-1234-5678");
        assertThat(me.hireDate()).isEqualTo(LocalDate.of(2021, 7, 5));
        assertThat(me.roles()).containsExactly("EMPLOYEE", "TEAM_LEAD");
        assertThat(me.passwordChangeRequired()).isTrue();
    }

    @Test
    void 내_정보_조회_시_직원이_없으면_직원_없음_오류를_낸다() {
        when(employeeRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(EMPLOYEE_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    // --- 테스트 데이터 ---

    private Employee 직원(boolean passwordChangeRequired) {
        Employee employee = Employee.builder()
                .email("user@company.com")
                .passwordHash("$2a$10$hash")
                .name("테스트사원")
                .phone("010-1234-5678")
                .hireDate(LocalDate.of(2021, 7, 5))
                .status(EmployeeStatus.ACTIVE)
                .roles(Set.of(Role.EMPLOYEE, Role.TEAM_LEAD))
                .build();
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        if (passwordChangeRequired) {
            employee.requirePasswordChange();
        }
        return employee;
    }

    private void 로그인된_상태(Employee employee) {
        UserPrincipal principal = UserPrincipal.from(employee);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
