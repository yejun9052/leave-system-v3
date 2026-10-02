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
 * 공지 메일 발송. 받는 사람(최대 전 직원)끼리 주소가 보이지 않도록 받는 사람 칸에는 보내는 주소를,
 * 실제 받는 사람은 숨은 참조(BCC)로 넣어 한 통만 보낸다.
 * 요청 트랜잭션이 커밋된 뒤 별도 스레드에서 보내며, 실패는 로그로만 남긴다.
 */
@Service
public class AnnouncementMailService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementMailService.class);

    private final JavaMailSender mailSender;
    private final AccountMailProperties properties;

    public AnnouncementMailService(JavaMailSender mailSender, AccountMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnnouncement(AnnouncementMail mail) {
        if (mail.bcc().isEmpty()) {
            return;
        }
        try {
            mailSender.send(toMimeMessage(mail));
            log.info("[공지 메일] 발송 완료: {} → {}명", mail.subject(), mail.bcc().size());
        } catch (MailException | MessagingException ex) {
            log.warn("[공지 메일] 발송 실패: {} → {}명 ({})", mail.subject(), mail.bcc().size(), ex.getMessage());
        }
    }

    MimeMessage toMimeMessage(AnnouncementMail mail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(properties.from());
        helper.setBcc(mail.bcc().toArray(String[]::new));
        helper.setSubject(mail.subject());
        helper.setText(mail.text(), mail.html());
        return message;
    }
}
