package com.company.leave.auth;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.dto.LoginRequest;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.auth.dto.TokenResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       EmployeeRepository employeeRepository,
                       AuditService auditService,
                       LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // 브루트포스 방어: 연속 실패 임계치 초과 시 일정 시간 차단
        if (loginAttemptService.isBlocked(request.email())) {
            auditService.record(null, request.email(), "LOGIN", "auth", null,
                    "로그인 차단(시도 과다): " + request.email(), false);
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
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
        return issueTokens(employee.getId());
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Long employeeId = tokenProvider.parseEmployeeId(refreshToken, true);
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!employee.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        return issueTokens(employee.getId());
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return MeResponse.from(employee);
    }

    private TokenResponse issueTokens(Long employeeId) {
        String access = tokenProvider.createAccessToken(employeeId);
        String refresh = tokenProvider.createRefreshToken(employeeId);
        return TokenResponse.bearer(access, refresh, tokenProvider.accessTokenValiditySeconds());
    }
}
