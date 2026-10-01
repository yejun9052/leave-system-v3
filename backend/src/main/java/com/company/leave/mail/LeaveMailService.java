package com.company.leave.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 휴가 결재 메일 발송(신청·승인·반려·취소).
 * <p>{@link AccountMailService} 와 같이 요청 트랜잭션이 <b>커밋된 뒤</b> 별도 스레드에서 보내고,
 * 실패는 로그로만 남긴다(결재 처리에 영향 없음).
 * <p>같은 신청 건의 메일은 Message-ID / In-Reply-To / References 헤더로 한 대화로 묶는다.
 * 미리 정한 Message-ID 는 JavaMailSenderImpl 이 발송 시 그대로 유지한다.
 */
@Service
public class LeaveMailService {

    private static final Logger log = LoggerFactory.getLogger(LeaveMailService.class);

    private final JavaMailSender mailSender;
    private final AccountMailProperties properties;

    public LeaveMailService(JavaMailSender mailSender, AccountMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLeaveMail(LeaveMail mail) {
        if (mail.to().isEmpty()) {
            return;
        }
        try {
            mailSender.send(toMimeMessage(mail));
            log.info("[휴가 메일] 발송 완료: {} → {}", mail.subject(), mail.to());
        } catch (MailException | MessagingException ex) {
            log.warn("[휴가 메일] 발송 실패: {} → {} ({})", mail.subject(), mail.to(), ex.getMessage());
        }
    }

    MimeMessage toMimeMessage(LeaveMail mail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(mail.to().toArray(String[]::new));
        helper.setSubject(mail.subject());
        helper.setText(mail.body(), false);
        if (mail.messageId() != null) {
            message.setHeader("Message-ID", mail.messageId());
        }
        if (mail.inReplyTo() != null) {
            message.setHeader("In-Reply-To", mail.inReplyTo());
            message.setHeader("References", mail.inReplyTo());
        }
        return message;
    }
}
