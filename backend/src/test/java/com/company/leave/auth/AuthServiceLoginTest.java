package com.company.leave.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.dto.LoginRequest;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuthService#login 단위 테스트.
 * 인증·저장소·감사 로그는 목(mock)으로 대체하고, 세션 처리(ID 재발급·인덱스·CSRF 폐기)는
 * MockHttpServletRequest/Response 로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 로그인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthServiceLoginTest {

    private static final String EMAIL = "user@company.com";

    private static final String IP = "10.0.0.7";
    private static final String PASSWORD = "password1234!";
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
    /** 쿠키만 다루는 순수 객체라 목 대신 운영과 같은 설정의 실제 저장소 사용. */
    private final CsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    private AuthService authService;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, employeeRepository, auditService,
                loginAttemptService, securityContextRepository, csrfTokenRepository);
        request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(IP);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 성공하면_사용자_정보를_반환하고_인증_정보를_세션_저장소에_명시적으로_저장한다() {
        Authentication authenticated = 인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));

        MeResponse me = authService.login(new LoginRequest(EMAIL, PASSWORD), request, response);

        assertThat(me.id()).isEqualTo(EMPLOYEE_ID);
        assertThat(me.email()).isEqualTo(EMAIL);
        assertThat(me.role()).isEqualTo(Role.EMPLOYEE);

        ArgumentCaptor<SecurityContext> saved = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(saved.capture(), eq(request), eq(response));
        assertThat(saved.getValue().getAuthentication()).isSameAs(authenticated);
        verify(loginAttemptService).loginSucceeded(IP);
        verify(auditService).record(eq(EMPLOYEE_ID), anyString(), eq("LOGIN"), eq("auth"), isNull(),
                anyString(), eq(true));
    }

    @Test
    void 인증은_이메일과_비밀번호를_담은_미인증_토큰으로_AuthenticationManager에_위임한다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));

        authService.login(new LoginRequest(EMAIL, PASSWORD), request, response);

        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertThat(attempt.getValue().isAuthenticated()).isFalse();
        assertThat(attempt.getValue().getName()).isEqualTo(EMAIL);
        assertThat(attempt.getValue().getCredentials()).isEqualTo(PASSWORD);
    }

    @Test
    void 세션의_사용자별_조회_인덱스를_이메일이_아닌_직원_ID로_기록한다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));

        authService.login(new LoginRequest(EMAIL, PASSWORD), request, response);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false)
                .getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
                .isEqualTo(String.valueOf(EMPLOYEE_ID));
    }

    @Test
    void 세션_고정_방지를_위해_로그인_전_세션이_있으면_세션_ID를_재발급한다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));
        MockHttpSession preLoginSession = new MockHttpSession();
        request.setSession(preLoginSession);
        String before = preLoginSession.getId();

        authService.login(new LoginRequest(EMAIL, PASSWORD), request, response);

        assertThat(request.getSession(false).getId()).isNotEqualTo(before);
    }

    @Test
    void 로그인하면_기존_CSRF_토큰을_폐기한다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));
        request.setCookies(new Cookie("XSRF-TOKEN", "old-token"));

        authService.login(new LoginRequest(EMAIL, PASSWORD), request, response);

        // 쿠키 삭제(Max-Age=0) → 프론트가 GET /api/auth/csrf 로 새 토큰을 다시 받음
        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getMaxAge()).isZero();
    }

    @Test
    void 로그인_유지를_체크하면_세션을_30일로_늘리고_세션_쿠키를_브라우저를_닫아도_남게_한다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));

        authService.login(new LoginRequest(EMAIL, PASSWORD, true), request, response);

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(request.getSession(false).getAttribute("rememberMeUntil")).isInstanceOf(Long.class);
        assertThat(request.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR)).isEqualTo(true);
        verify(auditService).record(eq(EMPLOYEE_ID), anyString(), eq("LOGIN"), eq("auth"), isNull(),
                eq("로그인 성공(로그인 유지 30일)"), eq(true));
    }

    @Test
    void 로그인_유지를_체크하지_않으면_세션은_기본_만료를_따른다() {
        인증에_성공하는_직원(직원(EmployeeStatus.ACTIVE));
        int defaultInterval = new MockHttpSession().getMaxInactiveInterval();

        authService.login(new LoginRequest(EMAIL, PASSWORD, null), request, response);

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(defaultInterval);
        assertThat(request.getSession(false).getAttribute("rememberMeUntil")).isNull();
        assertThat(request.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR)).isNull();
    }

    @Test
    void 비밀번호가_틀리면_인증_실패로_처리하고_실패_횟수를_기록하며_세션을_만들지_않는다() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong!"), request, response))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(loginAttemptService).loginFailed(IP);
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
        assertThat(request.getSession(false)).isNull();
        verify(auditService).record(isNull(), eq(EMAIL), eq("LOGIN"), eq("auth"), isNull(),
                anyString(), eq(false));
    }

    @Test
    void 접속_IP가_막혀_있으면_인증을_시도하지_않고_남은_시간을_알려준다() {
        when(loginAttemptService.lockRemaining(IP)).thenReturn(java.util.Optional.of(java.time.Duration.ofSeconds(14 * 60 + 5)));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), request, response))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                    assertThat(ex.getMessage()).contains("이 PC").contains("15분 후");
                });

        verify(authenticationManager, never()).authenticate(any());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 재직_중이_아닌_계정은_비활성_계정_오류를_내고_세션을_만들지_않는다() {
        인증에_성공하는_직원(직원(EmployeeStatus.RESIGNED));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), request, response))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_INACTIVE));

        verify(securityContextRepository, never()).saveContext(any(), any(), any());
        verify(loginAttemptService, never()).loginSucceeded(anyString());
        assertThat(request.getSession(false)).isNull();
    }

    // --- 테스트 데이터 ---

    private Employee 직원(EmployeeStatus status) {
        Employee employee = Employee.builder()
                .email(EMAIL)
                .passwordHash("$2a$10$hash")
                .name("테스트사원")
                .status(status)
                .role(Role.EMPLOYEE)
                .build();
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        return employee;
    }

    /** 인증 성공 + 이메일로 직원 조회 성공 상황. */
    private Authentication 인증에_성공하는_직원(Employee employee) {
        UserPrincipal principal = UserPrincipal.from(employee);
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.of(employee));
        return authenticated;
    }
}
