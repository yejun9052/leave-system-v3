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

@DisplayName("휴가 결재 메일 발송")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveMailServiceTest {

    private JavaMailSender sender;
    private LeaveMailService service;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        service = new LeaveMailService(sender, new AccountMailProperties("no-reply@company.com", "http://localhost"));
    }

    @Test
    void 대화의_첫_메일은_정한_Message_ID_로_한_통에_모든_받는_사람에게_보낸다() throws Exception {
        service.onLeaveMail(new LeaveMail(List.of("a@company.com", "b@company.com"), "[연차관리] 휴가 결재 요청",
                "본문", "<p>본문</p>", "<leave-1-0.hr@company.com>", null));

        MimeMessage sent = sent();
        assertThat(sent.getHeader("Message-ID", null)).isEqualTo("<leave-1-0.hr@company.com>");
        assertThat(sent.getHeader("In-Reply-To")).isNull();
        assertThat(sent.getRecipients(Message.RecipientType.TO))
                .extracting(a -> ((InternetAddress) a).getAddress())
                .containsExactly("a@company.com", "b@company.com");
        assertThat(sent.getSubject()).isEqualTo("[연차관리] 휴가 결재 요청");
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("no-reply@company.com");
    }

    @Test
    void 답장은_In_Reply_To_와_References_로_같은_대화에_묶는다() throws Exception {
        service.onLeaveMail(new LeaveMail(List.of("a@company.com"), "RE: [연차관리] 휴가 신청 접수", "본문", "<p>본문</p>",
                null, "<leave-1-0.applicant@company.com>"));

        MimeMessage sent = sent();
        assertThat(sent.getHeader("In-Reply-To", null)).isEqualTo("<leave-1-0.applicant@company.com>");
        assertThat(sent.getHeader("References", null)).isEqualTo("<leave-1-0.applicant@company.com>");
        assertThat(sent.getHeader("Message-ID")).isNull();
    }

    @Test
    void HTML_본문과_일반_텍스트_본문을_함께_담는다() throws Exception {
        service.onLeaveMail(new LeaveMail(List.of("a@company.com"), "제목", "plain body", "<p>html body</p>",
                null, null));

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent().writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8))
                .contains("multipart/alternative")
                .contains("text/plain").contains("plain body")
                .contains("text/html").contains("<p>html body</p>");
    }

    @Test
    void 받는_사람이_없으면_보내지_않는다() {
        service.onLeaveMail(new LeaveMail(List.of(), "제목", "본문", "<p>본문</p>", null, null));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void 발송에_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> service.onLeaveMail(new LeaveMail(List.of("a@company.com"), "제목", "본문", "<p>본문</p>", null, null)))
                .doesNotThrowAnyException();
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }
}
