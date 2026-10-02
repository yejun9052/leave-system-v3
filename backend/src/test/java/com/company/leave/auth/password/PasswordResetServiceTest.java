package com.company.leave.auth.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.SessionTerminator;
import com.company.leave.auth.password.domain.PasswordResetToken;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.mail.AccountMailEvents;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 재설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-04T01:00:00Z");
    private static final String EMAIL = "user@company.com";
    private static final long EMPLOYEE_ID = 7L;

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SessionTerminator sessionTerminator;
    @Mock
    private ResetRequestLimiter requestLimiter;
    @Mock
    private AuditService auditService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, employeeRepository, passwordEncoder, eventPublisher,
                sessionTerminator, requestLimiter, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- 토큰 발급 ---

    @Test
    void 발급하면_이전_미사용_토큰을_지우고_새_토큰의_해시만_저장한다() {
        Employee employee = 직원(EmployeeStatus.ACTIVE);

        service.issue(employee);

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).deleteUnusedByEmployeeId(EMPLOYEE_ID);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        order.verify(tokenRepository).save(saved.capture());

        String rawToken = 발송된_토큰();
        assertThat(saved.getValue().getTokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(PasswordResetService.sha256Hex(rawToken))
                .isNotEqualTo(rawToken);
        assertThat(saved.getValue().getEmployeeId()).isEqualTo(EMPLOYEE_ID);
    }

    @Test
    void 발급한_토큰은_30분_동안_유효하다() {
        service.issue(직원(EmployeeStatus.ACTIVE));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void 토큰은_32바이트_Base64URL_원문으로_메일에_실린다() {
        service.issue(직원(EmployeeStatus.ACTIVE));

        assertThat(발송된_토큰()).matches("[A-Za-z0-9_-]{43}");
    }

    // --- 본인 요청 ---

    @Test
    void 재직_중인_등록_계정이면_재설정_메일을_보낸다() {
        when(requestLimiter.tryAcquire(EMAIL)).thenReturn(true);
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.of(직원(EmployeeStatus.ACTIVE)));

        service.requestReset(EMAIL);

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(eventPublisher).publishEvent(any(AccountMailEvents.PasswordReset.class));
    }

    @Test
    void 요청만으로는_기존_비밀번호를_바꾸지_않는다() {
        when(requestLimiter.tryAcquire(EMAIL)).thenReturn(true);
        Employee employee = 직원(EmployeeStatus.ACTIVE);
        String before = employee.getPasswordHash();
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.of(employee));

        service.requestReset(EMAIL);

        assertThat(employee.getPasswordHash()).isEqualTo(before);
        verifyNoInteractions(passwordEncoder, sessionTerminator);
    }

    @Test
    void 등록되지_않은_이메일이면_예외_없이_아무것도_하지_않는다() {
        when(requestLimiter.tryAcquire(EMAIL)).thenReturn(true);
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 퇴사자면_메일을_보내지_않는다() {
        when(requestLimiter.tryAcquire(EMAIL)).thenReturn(true);
        when(employeeRepository.findByEmail(EMAIL)).thenReturn(Optional.of(직원(EmployeeStatus.RESIGNED)));

        service.requestReset(EMAIL);

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 횟수_제한을_넘으면_계정을_조회하지도_않는다() {
        when(requestLimiter.tryAcquire(EMAIL)).thenReturn(false);

        service.requestReset(EMAIL);

        verifyNoInteractions(employeeRepository, tokenRepository, eventPublisher);
    }

    // --- 링크로 재설정 ---

    @Test
    void 유효한_토큰이면_비밀번호를_바꾸고_변경_요구를_풀고_모든_세션을_끊는다() {
        Employee employee = 직원(EmployeeStatus.ACTIVE);
        employee.requirePasswordChange();
        PasswordResetToken token = 토큰(NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHash(PasswordResetService.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(tokenRepository.markUsed(token.getId(), NOW)).thenReturn(1);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("new-hash");

        service.confirm("raw", "newPassword1!");

        assertThat(employee.getPasswordHash()).isEqualTo("new-hash");
        assertThat(employee.isPasswordChangeRequired()).isFalse();
        verify(sessionTerminator).terminateAll(EMPLOYEE_ID);
    }

    @Test
    void 만료된_토큰은_거부한다() {
        PasswordResetToken token = 토큰(NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        재설정이_거부되는지_확인("raw");
        verify(tokenRepository, never()).markUsed(anyLong(), any());
    }

    @Test
    void 이미_사용한_토큰은_두_번째_사용을_거부한다() {
        PasswordResetToken token = 토큰(NOW.plusSeconds(60));
        ReflectionTestUtils.setField(token, "usedAt", NOW.minusSeconds(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        재설정이_거부되는지_확인("raw");
    }

    @Test
    void 동시에_두_번_제출되면_먼저_사용된_쪽만_성공한다() {
        PasswordResetToken token = 토큰(NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(tokenRepository.markUsed(eq(token.getId()), eq(NOW))).thenReturn(0); // 다른 요청이 먼저 사용

        재설정이_거부되는지_확인("raw");
    }

    @Test
    void 없는_토큰은_거부한다() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        재설정이_거부되는지_확인("unknown");
    }

    // --- 테스트 도구 ---

    private void 재설정이_거부되는지_확인(String rawToken) {
        assertThatThrownBy(() -> service.confirm(rawToken, "newPassword1!"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        verifyNoInteractions(passwordEncoder, sessionTerminator);
    }

    private String 발송된_토큰() {
        ArgumentCaptor<AccountMailEvents.PasswordReset> event =
                ArgumentCaptor.forClass(AccountMailEvents.PasswordReset.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().email()).isEqualTo(EMAIL);
        return event.getValue().resetToken();
    }

    private Employee 직원(EmployeeStatus status) {
        Employee employee = Employee.builder()
                .email(EMAIL)
                .passwordHash("old-hash")
                .name("홍길동")
                .status(status)
                .build();
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        return employee;
    }

    private PasswordResetToken 토큰(Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken(EMPLOYEE_ID, "hash", NOW.minusSeconds(600), expiresAt);
        ReflectionTestUtils.setField(token, "id", 100L);
        return token;
    }
}
