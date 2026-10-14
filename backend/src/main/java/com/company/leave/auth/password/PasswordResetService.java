package com.company.leave.auth.password;

import com.company.leave.audit.AuditService;
import com.company.leave.auth.SessionTerminator;
import com.company.leave.auth.password.domain.PasswordResetToken;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.mail.AccountMailEvents;
import com.company.leave.mail.AccountMailTemplates;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 재설정(1회용 링크).
 * <ul>
 *   <li>토큰: SecureRandom 32바이트 → Base64URL. DB 에는 SHA-256 hex 만 저장</li>
 *   <li>유효 30분, 1회용. 새로 발급하면 그 사용자의 이전 미사용 토큰은 무효</li>
 *   <li>요청만으로는 기존 비밀번호를 바꾸지 않는다(링크만 발송)</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_VALIDITY = Duration.ofMinutes(AccountMailTemplates.RESET_LINK_VALID_MINUTES);
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionTerminator sessionTerminator;
    private final ResetRequestLimiter requestLimiter;
    private final AuditService auditService;
    private final Clock clock;
    /** 비밀번호 찾기 요청의 토큰 발급만 감싸는 트랜잭션. {@link #requestReset(String, String)} 참고 */
    private final TransactionOperations tx;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                EmployeeRepository employeeRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher,
                                SessionTerminator sessionTerminator,
                                ResetRequestLimiter requestLimiter,
                                AuditService auditService,
                                PlatformTransactionManager transactionManager) {
        this(tokenRepository, employeeRepository, passwordEncoder, eventPublisher, sessionTerminator,
                requestLimiter, auditService, Clock.systemUTC(), new TransactionTemplate(transactionManager));
    }

    PasswordResetService(PasswordResetTokenRepository tokenRepository,
                         EmployeeRepository employeeRepository,
                         PasswordEncoder passwordEncoder,
                         ApplicationEventPublisher eventPublisher,
                         SessionTerminator sessionTerminator,
                         ResetRequestLimiter requestLimiter,
                         AuditService auditService,
                         Clock clock,
                         TransactionOperations tx) {
        this.tokenRepository = tokenRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.sessionTerminator = sessionTerminator;
        this.requestLimiter = requestLimiter;
        this.auditService = auditService;
        this.clock = clock;
        this.tx = tx;
    }

    /**
     * 본인 요청(비밀번호 찾기). 등록 여부·제한 초과와 관계없이 호출부는 항상 같은 응답을 준다
     * (계정 존재 여부 노출 방지). 재직 중인 등록 계정이고 횟수 제한 이내일 때만 메일을 보낸다.
     */
    public void requestReset(String email) {
        requestReset(email, null);
    }

    /**
     * 응답은 항상 같다(계정 존재 여부를 알리지 않음). 이메일·IP 한도를 넘으면 조용히 보내지 않는다(DB 도 쓰지 않음).
     * 토큰 발급({@link #issue}, 같은 객체 호출이라 @Transactional 이 걸리지 않음)만 짧은 트랜잭션으로 감싸고,
     * 감사 기록(REQUIRES_NEW)은 그 트랜잭션이 끝난 뒤 남긴다: 안에서 남기면 한 요청이 DB 연결을 둘 쥔다(로그인과 같은 문제).
     */
    public void requestReset(String email, String clientIp) {
        if (!(clientIp == null ? requestLimiter.tryAcquire(email) : requestLimiter.tryAcquire(email, clientIp))) {
            log.warn("비밀번호 재설정 요청 제한 초과(이메일 1시간 {}회 · IP 1시간 {}회): {} / {}",
                    ResetRequestLimiter.MAX_REQUESTS, ResetRequestLimiter.MAX_REQUESTS_PER_IP, email, clientIp);
            return;
        }
        Employee issued = tx.execute(status -> employeeRepository.findByEmail(email.trim())
                .filter(Employee::isActive)
                .map(employee -> {
                    issue(employee);
                    return employee;
                })
                .orElse(null));
        if (issued != null) {
            auditService.record(issued.getId(), issued.getName(), "PASSWORD_RESET_REQUEST",
                    "auth", null, "비밀번호 재설정 링크 요청", true);
        }
    }

    /** 새 토큰 발급 + 재설정 메일 발송(커밋 후). 관리자 초기화에서도 사용. */
    @Transactional
    public void issue(Employee employee) {
        tokenRepository.deleteUnusedByEmployeeId(employee.getId());
        String rawToken = newRawToken();
        Instant now = clock.instant();
        tokenRepository.save(new PasswordResetToken(
                employee.getId(), sha256Hex(rawToken), now, now.plus(TOKEN_VALIDITY)));
        eventPublisher.publishEvent(
                new AccountMailEvents.PasswordReset(employee.getEmail(), employee.getName(), rawToken));
    }

    /** 링크로 새 비밀번호 설정. 성공 시 토큰 사용 처리, 변경 요구 해제, 그 사용자의 모든 세션 종료. */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        Employee employee = employeeRepository.findById(token.getEmployeeId())
                .filter(Employee::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        // 규칙에 안 맞으면 링크를 쓰지 않은 채로 둬서 같은 링크로 다시 시도할 수 있게
        PasswordPolicy.check(newPassword, employee.getEmail());
        if (tokenRepository.markUsed(token.getId(), now) != 1) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID); // 동시에 먼저 사용됨
        }
        employee.setOwnPassword(passwordEncoder.encode(newPassword));
        sessionTerminator.terminateAll(employee.getId());
        auditService.record(employee.getId(), employee.getName(), "PASSWORD_RESET", "auth", null,
                "재설정 링크로 비밀번호 변경", true);
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }
}
