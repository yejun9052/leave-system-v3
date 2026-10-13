package com.company.leave.auth;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.dto.LoginRequest;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.security.RememberMeSessionFilter;
import com.company.leave.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
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
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
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
    private final Clock clock = Clock.systemDefaultZone();

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
        // 브루트포스 방어: 계정이 아니라 접속한 PC(IP)에서 연속 실패가 많으면 그 IP의 로그인 시도를 잠시 막는다
        String clientIp = httpRequest.getRemoteAddr();
        java.util.Optional<java.time.Duration> locked = loginAttemptService.lockRemaining(clientIp);
        if (locked.isPresent()) {
            String who = loginIdForLog(request.email());
            auditService.record(null, who, "LOGIN", "auth", null,
                    "로그인 차단(시도 과다): " + who + " · IP " + clientIp, false);
            long minutes = Math.max(1, (locked.get().toSeconds() + 59) / 60);
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "이 PC에서 로그인에 여러 번 실패해 잠시 막혔습니다. " + minutes + "분 후 다시 시도하세요.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.loginFailed(clientIp);
            String who = loginIdForLog(request.email());
            auditService.record(null, who, "LOGIN", "auth", null,
                    "로그인 실패: " + who + " · IP " + clientIp, false);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Employee employee = employeeRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        loginAttemptService.loginSucceeded(clientIp);
        auditService.record(employee.getId(), employee.getName(), "LOGIN", "auth", null,
                request.rememberMe() ? "로그인 성공(로그인 유지 30일)" : "로그인 성공", true);
        establishSession(authentication, employee.getId(), httpRequest, httpResponse);
        if (request.rememberMe()) {
            RememberMeSessionFilter.start(httpRequest, clock);
        }
        return MeResponse.from(employee);
    }

    /**
     * 로그인 실패 기록에 남길 아이디. 있는 계정이면 그대로, 없는 아이디면 앞 두 글자만 남기고 가린다:
     * 아이디 칸에 비밀번호를 잘못 넣은 경우 평문 비밀번호가 이벤트 로그에 남지 않게.
     */
    String loginIdForLog(String typed) {
        String id = typed == null ? "" : typed.trim();
        if (!id.isEmpty() && employeeRepository.findByEmail(id).isPresent()) {
            return id;
        }
        int at = id.indexOf('@');
        String local = at >= 0 ? id.substring(0, at) : id;
        String masked = local.substring(0, Math.min(2, local.length())) + "***";
        return at >= 0 ? masked + id.substring(at) : masked + " (없는 아이디)";
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
     * 1) 기존 세션은 버리고 새로 만든다(세션 고정 방지, 이전 사람의 속성이 넘어오지 않게) + CSRF 토큰 폐기
     * 2) SecurityContext 를 세션에 명시 저장(Spring Security 6+ 는 자동 저장하지 않음)
     * 3) 사용자별 세션 조회용 인덱스를 직원 ID 로 기록(이메일은 변경될 수 있음)
     */
    private void establishSession(Authentication authentication, Long employeeId,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 같은 브라우저에 남아 있던 세션(먼저 로그인한 사람 등)은 버리고 새로 만든다: "로그인 유지" 같은 속성이
        // 새 로그인에 넘어오지 않게. 이 요청에서 이전 세션을 보고 켜 둔 쿠키 유지 표시도 지운다
        HttpSession previous = httpRequest.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        httpRequest.removeAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR);
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
