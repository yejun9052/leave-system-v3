package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 공지 메일 발송: 받는 사람 칸은 보내는 주소, 실제 받는 사람은 숨은 참조로 한 통. 받는 사람이 없으면 보내지 않고,
 * 발송 실패는 삼킨다(요청 처리에 영향 없음).
 */
@DisplayName("공지 메일 발송")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnouncementMailServiceTest {

    private JavaMailSender sender;
    private AnnouncementMailService service;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        service = new AnnouncementMailService(sender, new AccountMailProperties("no-reply@company.com", "http://localhost"));
    }

    @Test
    void 받는_사람끼리_주소가_보이지_않게_숨은_참조로_한_통만_보낸다() throws Exception {
        service.onAnnouncement(new AnnouncementMail(List.of("a@company.com", "b@company.com"),
                "[연차관리] 연차 사용 금지 기간 추가", "본문", "<p>본문</p>"));

        MimeMessage sent = sent();
        assertThat(sent.getRecipients(Message.RecipientType.TO))
                .extracting(a -> ((InternetAddress) a).getAddress()).containsExactly("no-reply@company.com");
        assertThat(sent.getRecipients(Message.RecipientType.BCC))
                .extracting(a -> ((InternetAddress) a).getAddress()).containsExactly("a@company.com", "b@company.com");
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("no-reply@company.com");
        assertThat(sent.getSubject()).isEqualTo("[연차관리] 연차 사용 금지 기간 추가");
    }

    @Test
    void HTML_본문과_일반_텍스트_본문을_함께_담는다() throws Exception {
        service.onAnnouncement(new AnnouncementMail(List.of("a@company.com"), "제목", "plain body", "<p>html body</p>"));

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent().writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8))
                .contains("multipart/alternative")
                .contains("text/plain").contains("plain body")
                .contains("text/html").contains("<p>html body</p>");
    }

    @Test
    void 받는_사람이_없으면_보내지_않는다() {
        service.onAnnouncement(new AnnouncementMail(List.of(), "제목", "본문", "<p>본문</p>"));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void 발송에_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new MailSendException("SMTP 연결 실패")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> service.onAnnouncement(
                new AnnouncementMail(List.of("a@company.com"), "제목", "본문", "<p>본문</p>")))
                .doesNotThrowAnyException();
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }
}
