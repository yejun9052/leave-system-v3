package com.company.leave.auth;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.dto.LoginRequest;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    /** 커스텀 JSON 로그인은 Spring 기본 로그인 필터를 거치지 않으므로 세션 고정 방지·CSRF 토큰 교체를 직접 적용. */
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
    private final CsrfLogoutHandler csrfLogoutHandler;

    public AuthService(AuthenticationManager authenticationManager,
                       EmployeeRepository employeeRepository,
                       AuditService auditService,
                       LoginAttemptService loginAttemptService,
                       SecurityContextRepository securityContextRepository,
                       CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
        this.csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
    }

    @Transactional(readOnly = true)
    public MeResponse login(LoginRequest request, HttpServletRequest httpRequest,
                            HttpServletResponse httpResponse) {
        // 브루트포스 방어: 연속 실패 임계치 초과 시 일정 시간 차단
        if (loginAttemptService.isBlocked(request.email())) {
            auditService.record(null, request.email(), "LOGIN", "auth", null,
                    "로그인 차단(시도 과다): " + request.email(), false);
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.loginFailed(request.email());
            auditService.record(null, request.email(), "LOGIN", "auth", null,
                    "로그인 실패: " + request.email(), false);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Employee employee = employeeRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        loginAttemptService.loginSucceeded(request.email());
        auditService.record(employee.getId(), employee.getName(), "LOGIN", "auth", null,
                "로그인 성공", true);
        establishSession(authentication, employee.getId(), httpRequest, httpResponse);
        return MeResponse.from(employee);
    }

    /** 현재 세션 삭제 + CSRF 토큰 폐기. */
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            auditService.record(principal.getId(), principal.getName(), "LOGOUT", "auth", null,
                    "로그아웃", true);
        }
        csrfLogoutHandler.logout(httpRequest, httpResponse, authentication);
        securityContextLogoutHandler.logout(httpRequest, httpResponse, authentication);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return MeResponse.from(employee);
    }

    /**
     * 인증 결과를 세션에 저장한다.
     * 1) 기존 세션이 있으면 세션 ID 재발급(세션 고정 방지) + CSRF 토큰 폐기
     * 2) SecurityContext 를 세션에 명시 저장(Spring Security 6+ 는 자동 저장하지 않음)
     * 3) 사용자별 세션 조회용 인덱스를 직원 ID 로 기록(이메일은 변경될 수 있음)
     */
    private void establishSession(Authentication authentication, Long employeeId,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        httpRequest.getSession().setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                SessionTerminator.principalName(employeeId));
    }
}
