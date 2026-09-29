package com.company.leave.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계정 메일 발송.
 * <p>요청 트랜잭션이 <b>커밋된 뒤</b>(롤백되면 발송 안 함) 별도 스레드에서 보낸다.
 * SMTP 지연·실패가 API 응답에 영향을 주지 않도록, 실패는 로그로만 남기고 예외를 던지지 않는다.
 */
@Service
public class AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);

    private final JavaMailSender mailSender;
    private final AccountMailProperties properties;

    public AccountMailService(JavaMailSender mailSender, AccountMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountCreated(AccountMailEvents.AccountCreated event) {
        send(event.email(), "계정 생성",
                AccountMailTemplates.accountCreated(properties.linkBaseUrl(), event.name(),
                        event.temporaryPassword()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordReset(AccountMailEvents.PasswordReset event) {
        send(event.email(), "비밀번호 재설정",
                AccountMailTemplates.passwordReset(properties.linkBaseUrl(), event.name(),
                        event.resetToken()));
    }

    private void send(String to, String kind, AccountMailTemplates.Mail mail) {
        // 본문(임시 비밀번호·토큰)은 로그에 남기지 않는다
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(mail.subject());
        message.setText(mail.body());
        try {
            mailSender.send(message);
            log.info("[{}] 메일 발송 완료: {}", kind, to);
        } catch (MailException ex) {
            log.warn("[{}] 메일 발송 실패: {} ({})", kind, to, ex.getMessage());
        }
    }
}
